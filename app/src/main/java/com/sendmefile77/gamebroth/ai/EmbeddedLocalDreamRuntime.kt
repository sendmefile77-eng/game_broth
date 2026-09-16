package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Owns the Local Dream native process that is packaged inside this APK.
 *
 * This is deliberately not an integration with the separately installed Local Dream application.
 * The game starts its own libstable_diffusion_core.so, binds it to loopback only and keeps it warm
 * while the game process is alive.
 */
internal class EmbeddedLocalDreamRuntime(
    private val context: Context = ImageBackendConfig.requireContext(),
) {
    private val mutex = Mutex()

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

    fun endpoint(path: String): String = "http://127.0.0.1:$PORT$path"

    fun runtimeInfo(): String {
        val proc = process
        return if (proc?.isAlive == true) {
            "Local Dream/QNN 2.48 · PID ${runCatching { proc.pid() }.getOrDefault(-1L)} · localhost:$PORT"
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
            if (marker.readTextOrNull() != QAIRT_BUILD_ID) {
                val assets = context.assets.list("qnnlibs").orEmpty().filter { it.endsWith(".so") }
                check(assets.isNotEmpty()) {
                    "APK не содержит QNN runtime. Соберите APK через обновлённый manual-android workflow."
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
                marker.writeText(QAIRT_BUILD_ID)
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
            "--port", PORT.toString(),
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
        Log.i(TAG, "Starting embedded Local Dream for ${modelDir.name}")
        val proc = try {
            ProcessBuilder(command)
                .directory(nativeDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = libraryPath
                    environment()["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath
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
                Log.i(TAG, "Embedded Local Dream is healthy on 127.0.0.1:$PORT")
                return null
            }
            Thread.sleep(350)
        }

        val detail = diagnosticTail()
        stopLocked()
        return buildString {
            append("Local Dream не поднял /health за время запуска")
            if (detail.isNotBlank()) append(": ").append(detail)
        }
    }

    private fun healthCheck(timeoutMs: Int): Boolean = runCatching {
        val connection = (URL(endpoint("/health")).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
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
        const val PORT = 18081
        const val QAIRT_BUILD_ID = "2.48.0.260626"
        const val RUNTIME_MARKER = ".qairt_runtime_version"
        const val START_TIMEOUT_MS = 180_000L
        const val MAX_LOG_CHARS = 24_000
        const val MAX_DIAGNOSTIC_CHARS = 8_000
    }
}
