package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Owns the Local Dream native process that is packaged inside this APK.
 *
 * The native core is never exposed to Wi-Fi/LAN: it binds to 127.0.0.1 only. Android apps share
 * the host network namespace, so loopback alone is not sufficient isolation. Every process start
 * therefore uses a fresh random port and a 256-bit in-memory token required by every HTTP route.
 * The token is passed only through the child-process environment and is never persisted or logged.
 */
internal class EmbeddedLocalDreamRuntime(
    private val context: Context = ImageBackendConfig.requireContext(),
) {
    private val mutex = Mutex()
    private val port: Int = chooseLoopbackPort()
    private val authToken: String = generateAuthToken()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var servingModelPath: String? = null

    @Volatile
    private var servingModelStamp: Long = -1L

    private val logLock = Any()
    private val recentLog = StringBuilder()

    suspend fun ensureRunning(): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val modelDir = ImageBackendConfig.extractedModelDir(context)
                ?: return@withContext ImageBackendConfig.modelImportStatus
            ImageBackendConfig.modelValidationError(modelDir)?.let { return@withContext it }

            val runtimeProblem = prepareBundledRuntime()
            if (runtimeProblem != null) return@withContext runtimeProblem

            val marker = File(modelDir, ImageBackendConfig.READY_MARKER)
            val stamp = marker.lastModified().takeIf { it > 0L } ?: modelDir.lastModified()
            val current = process
            if (
                current?.isAlive == true &&
                servingModelPath == modelDir.absolutePath &&
                servingModelStamp == stamp &&
                healthCheck(800)
            ) {
                return@withContext null
            }

            stopLocked()
            startLocked(modelDir, stamp)
        }
    }

    fun endpoint(path: String): String = "http://127.0.0.1:$port$path"

    /** Adds the per-process secret to an already-created loopback request. */
    fun authorize(connection: HttpURLConnection) {
        connection.setRequestProperty(AUTH_HEADER, authToken)
    }

    fun runtimeInfo(): String {
        val proc = process
        return if (proc?.isAlive == true) {
            "Local Dream/QNN 2.48 · процесс активен · private localhost"
        } else {
            "Local Dream/QNN 2.48 · процесс остановлен"
        }
    }

    fun diagnosticTail(): String = synchronized(logLock) {
        recentLog.toString().trim().takeLast(MAX_DIAGNOSTIC_CHARS)
    }

    private fun prepareBundledRuntime(): String? {
        return runCatching {
            val runtimeDir = ImageBackendConfig.runtimeDir(context)
            val marker = File(runtimeDir, RUNTIME_MARKER)
            if (marker.readTextOrNull() != TRUSTED_RUNTIME_BUILD_ID) {
                val assets = context.assets.list("qnnlibs").orEmpty().filter { it.endsWith(".so") }
                check(assets.isNotEmpty()) {
                    "APK не содержит QNN runtime. Соберите APK через обновлённый manual-android workflow."
                }

                // Replace the directory contents from scratch. Never keep an executable file that
                // came from an older importer/model archive or from a previous runtime version.
                runtimeDir.listFiles().orEmpty().forEach { child ->
                    check(child.deleteRecursively()) { "не удалось очистить старый QNN runtime: ${child.name}" }
                }

                assets.forEach { name ->
                    val target = File(runtimeDir, name)
                    context.assets.open("qnnlibs/$name").use { input ->
                        target.outputStream().buffered(4 * 1024 * 1024).use { output ->
                            input.copyTo(output, 4 * 1024 * 1024)
                        }
                    }
                    target.setReadable(true, true)
                    target.setExecutable(true, true)
                }
                marker.writeText(TRUSTED_RUNTIME_BUILD_ID)
            }

            val required = listOf("libQnnHtp.so", "libQnnSystem.so")
            val missing = required.filterNot { File(runtimeDir, it).isFile }
            check(missing.isEmpty()) { "QNN runtime неполный: нет ${missing.joinToString()}" }
            check(runtimeDir.listFiles().orEmpty().any { it.name.matches(Regex("libQnnHtpV.+Stub\\.so")) }) {
                "QNN runtime не содержит HTP Stub"
            }
            check(runtimeDir.listFiles().orEmpty().any { it.name.matches(Regex("libQnnHtpV.+Skel\\.so")) }) {
                "QNN runtime не содержит HTP Skel"
            }
            null
        }.getOrElse { it.message ?: it::class.java.simpleName }
    }

    private fun startLocked(modelDir: File, stamp: Long): String? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val executable = File(nativeDir, EXECUTABLE_NAME)
        if (!executable.isFile) {
            return "В APK нет $EXECUTABLE_NAME. Нужна сборка с embedded Local Dream core."
        }
        executable.setExecutable(true, true)

        val runtimeDir = ImageBackendConfig.runtimeDir(context)
        val command = mutableListOf(
            executable.absolutePath,
            "--type", "sdxl",
            "--model_dir", modelDir.absolutePath,
            "--port", port.toString(),
            "--lib_dir", runtimeDir.absolutePath,
            "--lowram",
        )

        val libraryPath = listOf(
            runtimeDir.absolutePath,
            nativeDir.absolutePath,
            "/system/lib64",
            "/vendor/lib64",
            "/vendor/lib64/egl",
        ).joinToString(":")

        synchronized(logLock) { recentLog.setLength(0) }
        Log.i(TAG, "Starting authenticated embedded Local Dream")
        val proc = try {
            ProcessBuilder(command)
                .directory(nativeDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = libraryPath
                    environment()["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath
                    environment()[AUTH_ENV] = authToken
                }
                .start()
        } catch (error: Throwable) {
            return "Не удалось запустить встроенный Local Dream: ${error.message ?: error::class.java.simpleName}"
        }

        process = proc
        servingModelPath = modelDir.absolutePath
        servingModelStamp = stamp
        startMonitor(proc)

        val deadline = System.nanoTime() + START_TIMEOUT_MS * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) {
                val detail = diagnosticTail().ifBlank { "код завершения ${runCatching { proc.exitValue() }.getOrDefault(-1)}" }
                stopLocked()
                return "Local Dream завершился при запуске: $detail"
            }
            if (healthCheck(1_000)) {
                Log.i(TAG, "Authenticated Local Dream is healthy on private loopback")
                return null
            }
            Thread.sleep(350)
        }

        val detail = diagnosticTail()
        stopLocked()
        return buildString {
            append("Local Dream не поднял защищённый /health за время запуска")
            if (detail.isNotBlank()) append(": ").append(detail)
        }
    }

    private fun healthCheck(timeoutMs: Int): Boolean = runCatching {
        val connection = (URL(endpoint("/health")).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            authorize(this)
        }
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun startMonitor(proc: Process) {
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Log.i(TAG, "Core: $line")
                        synchronized(logLock) {
                            recentLog.append(line).append('\n')
                            if (recentLog.length > MAX_LOG_CHARS) {
                                recentLog.delete(0, recentLog.length - MAX_DIAGNOSTIC_CHARS)
                            }
                        }
                    }
                }
                val code = proc.waitFor()
                if (process === proc) {
                    val detail = "Local Dream process exited: $code · ${diagnosticTail()}".take(2_000)
                    ImageBackendConfig.reportRuntimeError(detail)
                }
            } catch (error: Throwable) {
                if (process === proc) {
                    ImageBackendConfig.reportRuntimeError(
                        "Local Dream monitor: ${error.message ?: error::class.java.simpleName}",
                    )
                }
            }
        }, "GameBroth-localdream-log").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopLocked() {
        val proc = process
        process = null
        servingModelPath = null
        servingModelStamp = -1L
        if (proc != null && proc.isAlive) {
            runCatching { proc.destroy() }
            runCatching {
                if (!proc.waitFor(1, TimeUnit.SECONDS) && proc.isAlive) proc.destroyForcibly()
            }
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText().trim() else null }.getOrNull()

    private companion object {
        const val TAG = "EmbeddedLocalDream"
        const val EXECUTABLE_NAME = "libstable_diffusion_core.so"
        const val QAIRT_BUILD_ID = "2.48.0.260626"
        const val TRUSTED_RUNTIME_BUILD_ID = "$QAIRT_BUILD_ID-gamebroth-safe2"
        const val RUNTIME_MARKER = ".qairt_runtime_version"
        const val AUTH_ENV = "GAMEBROTH_LOCAL_TOKEN"
        const val AUTH_HEADER = "X-GameBroth-Token"
        const val START_TIMEOUT_MS = 180_000L
        const val MAX_LOG_CHARS = 24_000
        const val MAX_DIAGNOSTIC_CHARS = 8_000

        fun chooseLoopbackPort(): Int =
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
                socket.localPort
            }

        fun generateAuthToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
