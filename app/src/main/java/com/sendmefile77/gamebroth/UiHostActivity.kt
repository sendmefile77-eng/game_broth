package com.sendmefile77.gamebroth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.sendmefile77.gamebroth.ai.LocalDreamClient
import com.sendmefile77.gamebroth.ai.TellamaClient
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.VisualPromptBuilder
import com.sendmefile77.gamebroth.aitext.GameStateDigest
import com.sendmefile77.gamebroth.aitext.TextGenerationRequest
import com.sendmefile77.gamebroth.model.*
import com.sendmefile77.gamebroth.simulation.*
import com.sendmefile77.gamebroth.storage.GalleryFileStore
import com.sendmefile77.gamebroth.storage.SqliteGameRepository
import com.sendmefile77.gamebroth.ui.GameBrothUi
import com.sendmefile77.gamebroth.ui.UiGalleryFrame
import kotlinx.coroutines.launch

class UiHostActivity : ComponentActivity() {
    private lateinit var repository: SqliteGameRepository
    private lateinit var galleryStore: GalleryFileStore
    private val dayEngine = DayEngine()
    private val recruitmentEngine = RecruitmentEngine()

    private var gameState = androidx.compose.runtime.mutableStateOf<GameState?>(null)
    private var recentEvents = androidx.compose.runtime.mutableStateOf<List<WorldEvent>>(emptyList())
    private var latestReport = androidx.compose.runtime.mutableStateOf<DailyReport?>(null)
    private var dailyNarrative = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var diaries = androidx.compose.runtime.mutableStateOf<Map<String, String>>(emptyMap())
    private var locations = androidx.compose.runtime.mutableStateOf<List<RecruitmentLocation>>(emptyList())
    private var selectedLocation = androidx.compose.runtime.mutableStateOf<RecruitmentLocation?>(null)
    private var candidates = androidx.compose.runtime.mutableStateOf<List<RecruitCandidate>>(emptyList())
    private var visualProfiles = androidx.compose.runtime.mutableStateOf<Map<String, VisualIdentityProfile>>(emptyMap())
    private var galleries = androidx.compose.runtime.mutableStateOf<Map<String, List<UiGalleryFrame>>>(emptyMap())
    private var generatingStaffId = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var uiMessage = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var tellamaStatus = androidx.compose.runtime.mutableStateOf("не проверено")
    private var localDreamStatus = androidx.compose.runtime.mutableStateOf("не проверено")
    private var apiKey = androidx.compose.runtime.mutableStateOf("")

    private lateinit var tellama: TellamaClient
    private lateinit var localDream: LocalDreamClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SqliteGameRepository(this)
        galleryStore = GalleryFileStore(this)
        val prefs = getSharedPreferences(PREFS_AI, MODE_PRIVATE)
        apiKey.value = prefs.getString(KEY_TELLAMA_API_KEY, "").orEmpty()
        tellama = TellamaClient(apiKeyProvider = { apiKey.value })
        localDream = LocalDreamClient()

        gameState.value = repository.loadOrCreate()
        recentEvents.value = repository.recentWorldEvents(8)
        latestReport.value = repository.latestDailyReport()
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()

        setContent {
            GameBrothUi(
                state = gameState.value,
                events = recentEvents.value,
                report = latestReport.value,
                narrative = dailyNarrative.value,
                diaries = diaries.value,
                locations = locations.value,
                selectedLocation = selectedLocation.value,
                candidates = candidates.value,
                visualProfiles = visualProfiles.value,
                galleries = galleries.value,
                generatingStaffId = generatingStaffId.value,
                uiMessage = uiMessage.value,
                tellamaStatus = tellamaStatus.value,
                localDreamStatus = localDreamStatus.value,
                apiKey = apiKey.value,
                onApiKeyChange = { apiKey.value = it },
                onSaveApiKey = {
                    prefs.edit().putString(KEY_TELLAMA_API_KEY, apiKey.value.trim()).apply()
                    tellamaStatus.value = "ключ сохранён"
                },
                onAdvanceDay = ::advanceDay,
                onCheckAi = ::checkAi,
                onSelectLocation = ::selectLocation,
                onRecruitBack = { selectedLocation.value = null; candidates.value = emptyList() },
                onHire = ::hire,
                onGeneratePortrait = { generateStaffFrame(it, GalleryFrameRole.PORTRAIT, "single full-height casting portrait in a dark-fantasy private room") },
                onGenerateStoryFrame = { generateStaffFrame(it, GalleryFrameRole.SCENE, "new quiet after-work character scene in her room, different pose and framing, showing current accessories and clothing") },
                onMakeCanonical = ::makeCanonical,
                onToggleIdentityLock = ::toggleIdentityLock,
            )
        }
    }

    private fun advanceDay() {
        val current = gameState.value ?: return
        val result = dayEngine.advanceDay(current)
        repository.saveState(result.state)
        repository.saveDailyReport(result.report)
        result.events.forEach(repository::appendWorldEvent)
        result.memories.forEach(repository::appendMemory)
        gameState.value = result.state
        latestReport.value = result.report
        dailyNarrative.value = null
        recentEvents.value = repository.recentWorldEvents(8)
        selectedLocation.value = null
        candidates.value = emptyList()
        uiMessage.value = "День ${result.report.day} завершён."
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()
        narrate(result)
    }

    private fun narrate(result: DayResult) {
        if (apiKey.value.isBlank()) return
        lifecycleScope.launch {
            val facts = buildString {
                result.report.staffReports.forEach { sr ->
                    append(sr.staffName).append(" level ").append(sr.levelBefore).append("->").append(sr.levelAfter).append('\n')
                    sr.encounters.forEachIndexed { index, encounter ->
                        append("client ").append(index + 1).append(": ")
                            .append(encounter.client.displayName).append(" / ")
                            .append(encounter.serviceCode).append(" / ")
                            .append(encounter.outcome.name).append(" / ")
                            .append(encounter.summary).append('\n')
                    }
                    sr.purchase?.let { append("purchase: ").append(it.item.name).append(" for ").append(it.price).append('\n') }
                }
                append("treasuryAfter=").append(result.report.treasuryAfter)
                    .append(" upkeep=").append(result.report.upkeep)
                    .append(" debtDelta=").append(result.report.debtDelta)
            }
            val output = runCatching {
                tellama.generate(
                    TextGenerationRequest(
                        systemPrompt = "Все персонажи совершеннолетние. Преврати только переданные факты в живой короткий русский игровой текст. Не меняй числа, исходы и личные границы. Немного сухого чёрного юмора допустимо.",
                        stateDigest = GameStateDigest.from(result.state),
                        playerAction = facts,
                        recentEvents = result.events,
                        maxTokens = 700,
                        temperature = .72,
                    ),
                )
            }.getOrNull()
            output?.content?.let { dailyNarrative.value = it }
        }
    }

    private fun refreshRecruitment() {
        gameState.value?.let { locations.value = recruitmentEngine.locations(it) }
    }

    private fun refreshDiaries() {
        val state = gameState.value ?: return
        diaries.value = state.staff.mapNotNull { member ->
            repository.memoriesForStaff(member.id, 30)
                .firstOrNull { it.category == "diary" }
                ?.let { member.id to it.summary }
        }.toMap()
    }

    private fun refreshVisualMemory() {
        val state = gameState.value ?: return
        val profileMap = mutableMapOf<String, VisualIdentityProfile>()
        val galleryMap = mutableMapOf<String, List<UiGalleryFrame>>()
        state.staff.forEach { member ->
            val profile = repository.visualProfile(member.id)
                ?: VisualIdentityFactory.fromStaff(member, state.worldSeed, state.currentDay).also(repository::saveVisualProfile)
            profileMap[member.id] = profile
            galleryMap[member.id] = repository.galleryForStaff(member.id)
                .map { UiGalleryFrame(it, galleryStore.absolutePath(it.localPath)) }
        }
        visualProfiles.value = profileMap
        galleries.value = galleryMap
    }

    private fun selectLocation(location: RecruitmentLocation) {
        val state = gameState.value ?: return
        selectedLocation.value = location
        candidates.value = recruitmentEngine.candidates(state, location)
        uiMessage.value = null
    }

    private fun hire(candidate: RecruitCandidate) {
        val current = gameState.value ?: return
        val result = runCatching { recruitmentEngine.hire(current, candidate) }.getOrElse {
            uiMessage.value = if (it.message?.contains("treasury", true) == true) "Не хватает денег на найм." else it.message
            return
        }
        repository.saveState(result.state)
        repository.appendWorldEvent(result.event)
        repository.saveVisualProfile(VisualIdentityFactory.fromCandidate(candidate, result.state.worldSeed, result.state.currentDay))
        gameState.value = result.state
        recentEvents.value = repository.recentWorldEvents(8)
        selectedLocation.value = null
        candidates.value = emptyList()
        uiMessage.value = "${candidate.name} теперь работает у вас."
        refreshVisualMemory()
    }

    private fun generateStaffFrame(staffId: String, role: GalleryFrameRole, scene: String) {
        val state = gameState.value ?: return
        val member = state.staff.firstOrNull { it.id == staffId } ?: return
        val profile = repository.visualProfile(staffId)
            ?: VisualIdentityFactory.fromStaff(member, state.worldSeed, state.currentDay).also(repository::saveVisualProfile)
        if (generatingStaffId.value != null) {
            uiMessage.value = "Изображение уже создаётся."
            return
        }
        generatingStaffId.value = staffId
        uiMessage.value = "Создаётся новый кадр для ${member.name}…"
        lifecycleScope.launch {
            try {
                val canonicalFrame = if (role == GalleryFrameRole.PORTRAIT) null
                    else profile.canonicalFrameId?.let(repository::galleryFrame)
                val referenceBytes = canonicalFrame?.let { galleryStore.read(it.localPath) }
                val built = VisualPromptBuilder.build(member, profile, role, scene, member.inventory)
                val ordinal = repository.galleryForStaff(staffId).size
                val seed = state.worldSeed xor staffId.hashCode().toLong() xor
                    (state.currentDay.toLong() shl 24) xor (ordinal.toLong() shl 8) xor role.ordinal.toLong()
                val result = localDream.generate(
                    ImageGenerationRequest(
                        prompt = built.prompt,
                        negativePrompt = built.negativePrompt,
                        width = built.width,
                        height = built.height,
                        steps = 12,
                        cfgScale = 4.0,
                        seed = seed,
                        cacheKey = "staff/$staffId/day/${state.currentDay}/$ordinal",
                        referenceImageBytes = referenceBytes,
                        referenceStrength = 0.84,
                    ),
                ) ?: error("генератор не вернул изображение")

                val frameId = "frame-${state.currentDay}-${staffId}-${ordinal + 1}"
                val relativePath = galleryStore.savePng(staffId, frameId, result.bytes)
                val frame = GalleryFrame(
                    id = frameId,
                    staffId = staffId,
                    day = state.currentDay,
                    role = role,
                    localPath = relativePath,
                    prompt = built.prompt,
                    negativePrompt = built.negativePrompt,
                    seed = result.seed ?: seed,
                    width = result.width.takeIf { it > 0 } ?: built.width,
                    height = result.height.takeIf { it > 0 } ?: built.height,
                    referenceFrameId = canonicalFrame?.id,
                    profileRevision = profile.revision,
                    createdAtEpochMs = System.currentTimeMillis(),
                    canonical = false,
                )
                repository.saveGalleryFrame(frame)
                refreshVisualMemory()
                uiMessage.value = if (role == GalleryFrameRole.PORTRAIT && profile.canonicalFrameId == null)
                    "Портрет сохранён. Если внешность удачная — назначьте его эталоном."
                else "Новый кадр сохранён в галерею ${member.name}."
            } catch (error: Throwable) {
                uiMessage.value = "Изображение не получено: ${error.message ?: error::class.java.simpleName}"
            } finally {
                generatingStaffId.value = null
            }
        }
    }

    private fun makeCanonical(staffId: String, frameId: String) {
        val updated = repository.setCanonicalFrame(staffId, frameId)
        uiMessage.value = if (updated == null) "Не удалось сохранить эталон." else "Эталон внешности выбран."
        refreshVisualMemory()
    }

    private fun toggleIdentityLock(staffId: String) {
        val current = repository.visualProfile(staffId) ?: return
        repository.setVisualIdentityLocked(staffId, !current.locked)
        uiMessage.value = if (current.locked) "Канон внешности разблокирован." else "Канон внешности закреплён."
        refreshVisualMemory()
    }

    private fun checkAi() {
        lifecycleScope.launch {
            tellamaStatus.value = "проверяем…"
            localDreamStatus.value = "проверяем…"
            val text = tellama.status(true)
            tellamaStatus.value = if (text.available) "готово: ${text.model}" else "недоступно: ${text.detail}"
            val image = localDream.status(true)
            localDreamStatus.value = if (image.available) "готово" else "недоступно: ${image.detail}"
        }
    }

    companion object {
        const val PREFS_AI = "ai_settings"
        const val KEY_TELLAMA_API_KEY = "tellama_api_key"
    }
}
