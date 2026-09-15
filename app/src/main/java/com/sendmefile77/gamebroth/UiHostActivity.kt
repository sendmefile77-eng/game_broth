package com.sendmefile77.gamebroth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.sendmefile77.gamebroth.ai.LocalDreamClient
import com.sendmefile77.gamebroth.ai.TellamaClient
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ScenePromptPlanner
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
                onGeneratePortrait = { generateStaffFrame(it, GalleryFrameRole.PORTRAIT, "neutral full-height identity portrait") },
                onGenerateStoryFrame = ::generateDayFrame,
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
        uiMessage.value = "День ${result.report.day} завершён. Теперь «Кадр дня» использует события именно этого дня."
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
                    val after = result.state.staff.firstOrNull { it.id == sr.staffId }
                    append(sr.staffName).append("; level ").append(sr.levelBefore).append("->").append(sr.levelAfter)
                        .append("; businessRevenue=").append(sr.businessRevenue)
                        .append("; personalRevenue=").append(sr.personalRevenue).append('\n')
                    sr.encounters.forEachIndexed { index, encounter ->
                        append("client ").append(index + 1).append(": ")
                            .append(encounter.client.displayName).append(" / ")
                            .append(encounter.client.archetype).append(" / ")
                            .append(encounter.serviceCode).append(" / ")
                            .append(encounter.outcome.name).append(" / ")
                            .append(encounter.summary).append('\n')
                    }
                    sr.incident?.let { append("incident: ").append(it).append('\n') }
                    sr.purchase?.let { append("purchase: ").append(it.item.name).append(" for ").append(it.price).append("; reason=").append(it.reason).append('\n') }
                    after?.let {
                        append("end condition: fatigue=").append(it.fatigue)
                            .append(" stress=").append(it.stress)
                            .append(" health=").append(it.health)
                            .append(" loyalty=").append(it.loyalty).append('\n')
                    }
                }
                append("treasuryAfter=").append(result.report.treasuryAfter)
                    .append(" upkeep=").append(result.report.upkeep)
                    .append(" debtDelta=").append(result.report.debtDelta)
            }
            val output = runCatching {
                tellama.generate(
                    TextGenerationRequest(
                        systemPrompt = "Все персонажи совершеннолетние. На основе ТОЛЬКО переданных фактов напиши содержательную хронику завершённого дня на русском: 4–8 коротких абзацев в зависимости от числа сотрудниц и событий. Для каждой сотрудницы упомяни конкретные встречи, итог смены, настроение/состояние, покупку или происшествие, если они были. Не придумывай новых клиентов, событий, чисел или мотивов. Не меняй исходы и личные границы. Текст должен ощущаться как жизнь заведения, а не как бухгалтерская сводка; допустим сухой чёрный юмор. Интимные события описывай без графических анатомических подробностей.",
                        stateDigest = GameStateDigest.from(result.state),
                        playerAction = facts,
                        recentEvents = result.events,
                        maxTokens = 1100,
                        temperature = .68,
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

    private fun generateDayFrame(staffId: String) {
        val state = gameState.value ?: return
        val member = state.staff.firstOrNull { it.id == staffId } ?: return
        val report = latestReport.value
        if (report == null) {
            uiMessage.value = "Сначала завершите хотя бы один день: «Кадр дня» строится только по реальным итогам смены."
            return
        }
        val staffReport = report.staffReports.firstOrNull { it.staffId == staffId }
        if (staffReport == null) {
            uiMessage.value = "В последнем завершённом дне у ${member.name} нет отчёта для кадра."
            return
        }
        val ordinal = repository.galleryForStaff(staffId).size
        val plan = ScenePromptPlanner.report(staffId, report.day, ordinal).asPrompt()
        val scene = "$plan ${buildDaySceneFacts(report, staffReport, member)}"
        generateStaffFrame(staffId, GalleryFrameRole.EVENT, scene, report.day)
    }

    private fun buildDaySceneFacts(report: DailyReport, staffReport: StaffDayReport, member: StaffMember): String {
        val notable = staffReport.encounters.maxByOrNull { encounterWeight(it.outcome) }
        val dayTone = when {
            staffReport.incident != null -> "The day ended tense and draining after a difficult incident."
            notable?.outcome == EncounterOutcome.EXCELLENT -> "The day ended on a visibly successful, relieved note."
            member.fatigue >= 75 -> "The day ended in heavy physical fatigue."
            member.stress >= 70 -> "The day ended with visible tension and the need for quiet."
            else -> "The day ended as a believable ordinary working night."
        }
        val actionFact = when {
            staffReport.purchase != null -> "She is handling or putting away the item she bought today: ${staffReport.purchase.item.name}."
            staffReport.incident != null -> "She has withdrawn to a quieter part of the establishment to recover after the difficult encounter."
            staffReport.businessRevenue > 0 -> "She is winding down after work, with a few coins, cups or signs of the completed shift nearby."
            else -> "She is resting quietly at the end of the day while the establishment closes around her."
        }
        val notableFact = notable?.let {
            "A notable visitor today was ${it.client.displayName}, ${it.client.archetype}; outcome ${it.outcome.name.lowercase()}: ${it.summary}"
        } ?: "There were no completed client encounters to depict directly."
        return buildString {
            append("THIS IS THE VISUAL SUMMARY OF COMPLETED DAY ${report.day}, NOT A GENERIC PORTRAIT. ")
            append("The brothel/establishment ambience is mandatory and must occupy a substantial part of the frame. ")
            append("Show believable end-of-shift surroundings: warm oil lamps, curtains, worn furniture, a staff room/common room/corridor, cups, coins, folded clothes or other subtle traces of the working day. ")
            append("No photographic equipment of any kind. No photographer. No cameras, lenses or tripods. ")
            append("Only ${member.name} is visible; other visitors are implied only through the aftermath and environment. ")
            append("Factual context: ${staffReport.encounters.size} visitor(s) handled; business earned ${staffReport.businessRevenue}; personal share ${staffReport.personalRevenue}. ")
            append(dayTone).append(' ')
            append(actionFact).append(' ')
            append(notableFact).append(' ')
            staffReport.incident?.let { append("Incident fact: $it ") }
            staffReport.purchase?.let { append("Purchase fact: ${it.item.name}, price ${it.price}. ") }
            append("End condition: fatigue ${member.fatigue}/100, stress ${member.stress}/100, health ${member.health}/100. ")
            append("Do not invent an unrelated prop or replace the establishment with a studio/product-shot setting.")
        }
    }

    private fun encounterWeight(outcome: EncounterOutcome): Int = when (outcome) {
        EncounterOutcome.INCIDENT -> 6
        EncounterOutcome.REFUSED -> 5
        EncounterOutcome.EXCELLENT -> 4
        EncounterOutcome.AWKWARD -> 3
        EncounterOutcome.GOOD -> 2
        EncounterOutcome.ROUTINE -> 1
    }

    private fun generateStaffFrame(
        staffId: String,
        role: GalleryFrameRole,
        requestedScene: String,
        requestedDay: Int? = null,
    ) {
        val state = gameState.value ?: return
        val member = state.staff.firstOrNull { it.id == staffId } ?: return
        val profile = repository.visualProfile(staffId)
            ?: VisualIdentityFactory.fromStaff(member, state.worldSeed, state.currentDay).also(repository::saveVisualProfile)
        if (generatingStaffId.value != null) {
            uiMessage.value = "Изображение уже создаётся."
            return
        }
        val generationDay = requestedDay ?: state.currentDay
        generatingStaffId.value = staffId
        uiMessage.value = if (role == GalleryFrameRole.EVENT) "Создаётся кадр итогов дня для ${member.name}…" else "Создаётся новый кадр для ${member.name}…"
        lifecycleScope.launch {
            try {
                val ordinal = repository.galleryForStaff(staffId).size
                val scene = when (role) {
                    GalleryFrameRole.PORTRAIT -> requestedScene
                    GalleryFrameRole.SCENE -> requestedScene.ifBlank { ScenePromptPlanner.story(staffId, generationDay, ordinal).asPrompt() }
                    GalleryFrameRole.EVENT -> requestedScene.ifBlank { ScenePromptPlanner.report(staffId, generationDay, ordinal).asPrompt() }
                }
                val built = VisualPromptBuilder.build(member, profile, role, scene, member.inventory)

                // Local Dream's `image` input is img2img, not a face/identity adapter.
                // A full canonical PNG would lock pose/background/composition too, so scene identity
                // currently comes from the persistent textual VisualIdentityProfile instead.
                val referenceBytes: ByteArray? = null

                val sceneHash = scene.hashCode().toLong() and 0xffffffffL
                val seed = state.worldSeed xor staffId.hashCode().toLong() xor
                    (generationDay.toLong() shl 24) xor (ordinal.toLong() shl 8) xor
                    role.ordinal.toLong() xor sceneHash
                val cacheKey = "staff/$staffId/${role.name.lowercase()}/day/$generationDay/$ordinal/$sceneHash"
                val result = localDream.generate(
                    ImageGenerationRequest(
                        prompt = built.prompt,
                        negativePrompt = built.negativePrompt,
                        width = built.width,
                        height = built.height,
                        steps = 14,
                        cfgScale = 4.2,
                        seed = seed,
                        cacheKey = cacheKey,
                        referenceImageBytes = referenceBytes,
                    ),
                ) ?: error("генератор не вернул изображение")

                val frameId = "frame-$generationDay-$staffId-${role.name.lowercase()}-${ordinal + 1}"
                val relativePath = galleryStore.savePng(staffId, frameId, result.bytes)
                val frame = GalleryFrame(
                    id = frameId,
                    staffId = staffId,
                    day = generationDay,
                    role = role,
                    localPath = relativePath,
                    prompt = built.prompt,
                    negativePrompt = built.negativePrompt,
                    seed = result.seed ?: seed,
                    width = result.width.takeIf { it > 0 } ?: built.width,
                    height = result.height.takeIf { it > 0 } ?: built.height,
                    referenceFrameId = null,
                    profileRevision = profile.revision,
                    createdAtEpochMs = System.currentTimeMillis(),
                    canonical = false,
                )
                repository.saveGalleryFrame(frame)
                refreshVisualMemory()
                uiMessage.value = when {
                    role == GalleryFrameRole.PORTRAIT && profile.canonicalFrameId == null -> "Портрет сохранён. Если внешность удачная — назначьте его эталоном."
                    role == GalleryFrameRole.EVENT -> "Кадр дня ${generationDay} сохранён: он построен по реальному отчёту и антуражу заведения."
                    else -> "Новый, отдельный кадр сохранён в галерею ${member.name}."
                }
            } catch (error: Throwable) {
                uiMessage.value = "Изображение не получено: ${error.message ?: error::class.java.simpleName}"
            } finally {
                generatingStaffId.value = null
            }
        }
    }

    private fun makeCanonical(staffId: String, frameId: String) {
        val updated = repository.setCanonicalFrame(staffId, frameId)
        uiMessage.value = if (updated == null) "Не удалось сохранить эталон." else "Эталонный кадр выбран. Внешность закреплена отдельным профилем."
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
