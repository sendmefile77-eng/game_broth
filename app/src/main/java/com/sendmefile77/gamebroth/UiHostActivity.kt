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
import kotlinx.coroutines.async
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
    private var narrativeLoading = androidx.compose.runtime.mutableStateOf(false)
    private var daySceneLoading = androidx.compose.runtime.mutableStateOf(false)
    private var dayProcessing = androidx.compose.runtime.mutableStateOf(false)
    private var homeDayScene = androidx.compose.runtime.mutableStateOf<UiGalleryFrame?>(null)
    private var diaries = androidx.compose.runtime.mutableStateOf<Map<String, String>>(emptyMap())
    private var locations = androidx.compose.runtime.mutableStateOf<List<RecruitmentLocation>>(emptyList())
    private var selectedLocation = androidx.compose.runtime.mutableStateOf<RecruitmentLocation?>(null)
    private var candidates = androidx.compose.runtime.mutableStateOf<List<RecruitCandidate>>(emptyList())
    private var candidatePortraits = androidx.compose.runtime.mutableStateOf<Map<String, String>>(emptyMap())
    private var generatingCandidateId = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var visualProfiles = androidx.compose.runtime.mutableStateOf<Map<String, VisualIdentityProfile>>(emptyMap())
    private var galleries = androidx.compose.runtime.mutableStateOf<Map<String, List<UiGalleryFrame>>>(emptyMap())
    private var generatingStaffId = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var uiMessage = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var tellamaStatus = androidx.compose.runtime.mutableStateOf("не проверено")
    private var localDreamStatus = androidx.compose.runtime.mutableStateOf("не проверено")
    private var apiKey = androidx.compose.runtime.mutableStateOf("")

    private val candidatePreviewMeta = mutableMapOf<String, RecruitPreviewMeta>()
    private var recruitPreviewBatch = 0

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
        latestReport.value = repository.latestDailyReport()
        recentEvents.value = loadGameplayEvents()
        dailyNarrative.value = loadLatestNarrative()
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()

        setContent {
            GameBrothUi(
                state = gameState.value,
                events = recentEvents.value,
                report = latestReport.value,
                narrative = dailyNarrative.value,
                narrativeLoading = narrativeLoading.value,
                homeDayScene = homeDayScene.value,
                daySceneLoading = daySceneLoading.value,
                dayProcessing = dayProcessing.value,
                diaries = diaries.value,
                locations = locations.value,
                selectedLocation = selectedLocation.value,
                candidates = candidates.value,
                candidatePortraits = candidatePortraits.value,
                generatingCandidateId = generatingCandidateId.value,
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
                onSetStaffPlan = ::setStaffPlan,
                onCheckAi = ::checkAi,
                onSelectLocation = ::selectLocation,
                onRecruitBack = ::leaveRecruitmentLocation,
                onHire = ::hire,
                onGeneratePortrait = { generateStaffFrame(it, GalleryFrameRole.PORTRAIT, "full body, head to toe, standing, seductive pose") },
                onMakeCanonical = ::makeCanonical,
                onToggleIdentityLock = ::toggleIdentityLock,
            )
        }
    }

    private fun setStaffPlan(staffId: String, status: StaffStatus) {
        if (status !in setOf(StaffStatus.AVAILABLE, StaffStatus.RESTING, StaffStatus.TRAINING)) return
        val current = gameState.value ?: return
        val member = current.staff.firstOrNull { it.id == staffId } ?: return
        if (member.status == StaffStatus.LEFT) return
        if (member.status == StaffStatus.INJURED) {
            uiMessage.value = "${member.name} травмирована: сегодня доступно только восстановление."
            return
        }
        val next = current.copy(
            staff = current.staff.map { if (it.id == staffId) it.copy(status = status) else it },
        )
        repository.saveState(next)
        gameState.value = next
        uiMessage.value = when (status) {
            StaffStatus.AVAILABLE -> "${member.name}: сегодня работает."
            StaffStatus.RESTING -> "${member.name}: сегодня отдыхает и восстанавливается."
            StaffStatus.TRAINING -> "${member.name}: сегодня учится; заведение оплатит материалы."
            else -> null
        }
    }

    private fun advanceDay() {
        if (dayProcessing.value) return
        val current = gameState.value ?: return
        dayProcessing.value = true
        val result = dayEngine.advanceDay(current)
        repository.saveState(result.state)
        repository.saveDailyReport(result.report)
        result.events.forEach(repository::appendWorldEvent)
        result.memories.forEach(repository::appendMemory)
        gameState.value = result.state
        latestReport.value = result.report

        val fallback = buildFallbackNarrative(result)
        dailyNarrative.value = fallback
        repository.appendWorldEvent(
            WorldEvent(
                id = "day-narrative-fallback-${result.report.day}",
                day = result.report.day,
                type = "DAY_NARRATIVE_FALLBACK",
                summary = fallback,
                payload = "source=engine",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        narrativeLoading.value = true
        daySceneLoading.value = true
        recentEvents.value = loadGameplayEvents()
        clearRecruitPreviewState()
        selectedLocation.value = null
        candidates.value = emptyList()
        uiMessage.value = "День ${result.report.day} завершён. Итоги уже видны; Qwen улучшает хронику, Local Dream создаёт новый кадр дня…"
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()

        lifecycleScope.launch {
            val textJob = async { narrate(result) }
            val imageJob = async { generateAutomaticDayScene(result) }
            textJob.await()
            imageJob.await()
            dayProcessing.value = false
            uiMessage.value = when {
                hasQwenNarrative(result.report.day) && homeDayScene.value?.frame?.day == result.report.day ->
                    "Итоги дня ${result.report.day} готовы: новый кадр и хроника Qwen обновлены."
                hasQwenNarrative(result.report.day) -> "Хроника Qwen дня ${result.report.day} готова; изображение не удалось обновить."
                homeDayScene.value?.frame?.day == result.report.day -> "Кадр дня ${result.report.day} готов; оставлена локальная хроника без Qwen."
                else -> "День ${result.report.day} завершён; оставлена локальная хроника, но изображение не обновилось."
            }
        }
    }

    private suspend fun narrate(result: DayResult) {
        try {
            val facts = buildString {
                append("completedDay=").append(result.report.day).append('\n')
                result.report.staffReports.forEach { sr ->
                    val after = result.state.staff.firstOrNull { it.id == sr.staffId }
                    append(sr.staffName).append("; level ").append(sr.levelBefore).append("->").append(sr.levelAfter)
                        .append("; businessRevenue=").append(sr.businessRevenue)
                        .append("; personalRevenue=").append(sr.personalRevenue).append('\n')
                    if (sr.encounters.isEmpty()) append("dayNote: ").append(sr.incident.orEmpty()).append('\n')
                    sr.encounters.forEachIndexed { index, encounter ->
                        append("client ").append(index + 1).append(": ")
                            .append(encounter.client.displayName).append(" / ")
                            .append(encounter.client.archetype).append(" / service=")
                            .append(encounter.serviceCode).append(" / outcome=")
                            .append(encounter.outcome.name).append(" / ")
                            .append(encounter.summary).append('\n')
                    }
                    sr.incident?.takeIf { sr.encounters.isNotEmpty() }?.let { append("incident: ").append(it).append('\n') }
                    sr.purchase?.let {
                        append("purchase: ").append(it.item.name).append(" for ").append(it.price)
                            .append("; reason=").append(it.reason).append('\n')
                    }
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
                        systemPrompt = "Ты хроникер взрослого тёмно-фэнтезийного борделя. Все персонажи совершеннолетние. Напиши по-русски живую, атмосферную хронику завершённого дня строго по переданным фактам. Нужно 4–7 коротких абзацев без списков и заголовков: сначала общий тон вечера, затем конкретные сотрудницы и посетители по именам, что реально происходило; отдельно учитывай распоряжения владельца — работа, отдых, обучение или восстановление; затем удачи, неловкости, отказы, инциденты, деньги и покупки только если они были; в конце — ощущение заведения после закрытия. Текст должен быть интересным и чувственным, с эротической атмосферой профессии и чёрным юмором там, где уместно, но без графических анатомических подробностей. Не придумывай новых людей, услуг, событий, мотивов, чисел или последствий. Не меняй исходы симуляции.",
                        stateDigest = GameStateDigest.from(result.state),
                        playerAction = facts,
                        recentEvents = result.events,
                        maxTokens = 1300,
                        temperature = .76,
                    ),
                )
            }.getOrNull()
            output?.content?.let { content ->
                dailyNarrative.value = content
                repository.appendWorldEvent(
                    WorldEvent(
                        id = "day-narrative-${result.report.day}",
                        day = result.report.day,
                        type = "DAY_NARRATIVE",
                        summary = content,
                        payload = "model=${output.model}",
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        } finally {
            narrativeLoading.value = false
        }
    }

    private fun buildFallbackNarrative(result: DayResult): String {
        val report = result.report
        val opening = when {
            report.grossRevenue >= 30 -> "Вечер выдался прибыльным: комнаты почти не пустовали, а к закрытию в кассе уже было что считать."
            report.grossRevenue > 0 -> "День прошёл без большого триумфа, но заведение всё-таки заработало и не стояло мёртвым."
            report.staffReports.any { it.incident.orEmpty().contains("обучение", ignoreCase = true) } -> "Сегодня часть жизни заведения ушла не в кассу, а в будущее: вместо клиентов было обучение."
            report.staffReports.any { it.incident.orEmpty().contains("отдых", ignoreCase = true) } -> "Сегодня хозяин сознательно пожертвовал частью выручки ради того, чтобы персонал не развалился раньше мебели."
            else -> "Вечер оказался тихим: денег почти не прибавилось, зато день всё равно оставил след в состоянии персонала."
        }
        val staffText = report.staffReports.joinToString("\n\n") { sr ->
            val after = result.state.staff.firstOrNull { it.id == sr.staffId }
            when {
                sr.encounters.isNotEmpty() -> {
                    val meetings = sr.encounters.take(3).joinToString(" ") { encounter ->
                        val outcome = when (encounter.outcome) {
                            EncounterOutcome.EXCELLENT -> "встреча прошла блестяще"
                            EncounterOutcome.GOOD -> "клиент ушёл довольным"
                            EncounterOutcome.ROUTINE -> "всё прошло привычно"
                            EncounterOutcome.AWKWARD -> "вышло неловко"
                            EncounterOutcome.REFUSED -> "на запрос пришлось ответить отказом"
                            EncounterOutcome.INCIDENT -> "встречу пришлось прервать"
                        }
                        "${encounter.client.displayName}, ${encounter.client.archetype}, пришёл на ${serviceNarrativeLabel(encounter.serviceCode)} — $outcome."
                    }
                    buildString {
                        append(sr.staffName).append(" отработала смену. ").append(meetings)
                        if (sr.personalRevenue > 0) append(" Её доля — ${sr.personalRevenue} галеонов.")
                        sr.purchase?.let { append(" После смены она купила ${it.item.name} за ${it.price}.") }
                        after?.let { append(" К закрытию: усталость ${it.fatigue}/100, стресс ${it.stress}/100, здоровье ${it.health}/100.") }
                    }
                }
                sr.incident.orEmpty().contains("обучение", ignoreCase = true) ->
                    "${sr.staffName} провела день на обучении. ${sr.incident.orEmpty()} К вечеру усталость ${after?.fatigue ?: 0}/100, стресс ${after?.stress ?: 0}/100."
                sr.incident.orEmpty().contains("восстанов", ignoreCase = true) || sr.incident.orEmpty().contains("травм", ignoreCase = true) ->
                    "${sr.staffName} не выходила на смену и восстанавливалась после травмы. К вечеру здоровье ${after?.health ?: 0}/100, усталость ${after?.fatigue ?: 0}/100."
                else ->
                    "${sr.staffName} получила день отдыха. К вечеру усталость ${after?.fatigue ?: 0}/100, стресс ${after?.stress ?: 0}/100, здоровье ${after?.health ?: 0}/100."
            }
        }
        val closing = buildString {
            append("После расходов в казне осталось ${report.treasuryAfter} галеонов")
            if (report.debtDelta > 0) append(", а долг вырос ещё на ${report.debtDelta}")
            append(". Заведение погасило лампы, но последствия сегодняшних решений уже перешли в следующий день.")
        }
        return listOf(opening, staffText, closing).filter(String::isNotBlank).joinToString("\n\n")
    }

    private fun serviceNarrativeLabel(code: String): String = when (code) {
        "conversation" -> "компанию и разговор"
        "massage" -> "массаж"
        "roleplay" -> "ролевую услугу"
        "private_intimacy" -> "приватную близость"
        "arcane_fantasy" -> "магическую фантазию"
        else -> code.replace('_', ' ')
    }

    private fun hasQwenNarrative(day: Int): Boolean = repository.recentWorldEvents(160)
        .any { it.type == "DAY_NARRATIVE" && it.day == day }

    private fun loadLatestNarrative(): String? {
        val day = latestReport.value?.day ?: return null
        val events = repository.recentWorldEvents(160).filter { it.day == day }
        return events.firstOrNull { it.type == "DAY_NARRATIVE" }?.summary
            ?: events.firstOrNull { it.type == "DAY_NARRATIVE_FALLBACK" }?.summary
    }

    private fun loadGameplayEvents(): List<WorldEvent> = repository.recentWorldEvents(50)
        .filterNot { it.type == "DAY_NARRATIVE" || it.type == "DAY_NARRATIVE_FALLBACK" }
        .take(8)

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
        refreshHomeDayScene()
    }

    private fun refreshHomeDayScene() {
        val reportDay = latestReport.value?.day
        if (reportDay == null) {
            homeDayScene.value = null
            return
        }
        homeDayScene.value = galleries.value.values.flatten()
            .filter { it.frame.role == GalleryFrameRole.EVENT && it.frame.day == reportDay }
            .maxByOrNull { it.frame.createdAtEpochMs }
    }

    private fun selectLocation(location: RecruitmentLocation) {
        val state = gameState.value ?: return
        val batchId = ++recruitPreviewBatch
        selectedLocation.value = location
        val nextCandidates = recruitmentEngine.candidates(state, location)
        candidates.value = nextCandidates
        candidatePortraits.value = emptyMap()
        candidatePreviewMeta.clear()
        generatingCandidateId.value = null
        uiMessage.value = "Подгружаем портреты кандидаток по очереди…"
        generateRecruitPreviewsSequentially(state, location, nextCandidates, batchId)
    }

    private fun leaveRecruitmentLocation() {
        clearRecruitPreviewState()
        selectedLocation.value = null
        candidates.value = emptyList()
        uiMessage.value = null
    }

    private fun clearRecruitPreviewState() {
        recruitPreviewBatch++
        generatingCandidateId.value = null
        candidatePortraits.value = emptyMap()
        candidatePreviewMeta.clear()
    }

    private fun generateRecruitPreviewsSequentially(
        state: GameState,
        location: RecruitmentLocation,
        batchCandidates: List<RecruitCandidate>,
        batchId: Int,
    ) {
        lifecycleScope.launch {
            val status = localDream.status(true)
            if (batchId != recruitPreviewBatch || selectedLocation.value?.id != location.id) return@launch
            if (!status.available) {
                generatingCandidateId.value = null
                uiMessage.value = "Local Dream недоступен: карточки останутся без портретов. ${status.detail.orEmpty()}".trim()
                return@launch
            }

            var successCount = 0
            batchCandidates.forEachIndexed { index, candidate ->
                if (batchId != recruitPreviewBatch || selectedLocation.value?.id != location.id) return@launch
                generatingCandidateId.value = candidate.id
                uiMessage.value = "Создаём портрет ${index + 1}/${batchCandidates.size}: ${candidate.name}…"

                val profile = VisualIdentityFactory.fromCandidate(candidate, state.worldSeed, state.currentDay)
                val previewStaff = candidate.asPreviewStaff()
                val built = VisualPromptBuilder.build(
                    previewStaff,
                    profile,
                    GalleryFrameRole.PORTRAIT,
                    "recruit, full body, head to toe, standing, seductive pose",
                )
                val seed = state.worldSeed xor candidate.id.hashCode().toLong() xor
                    (state.currentDay.toLong() shl 20) xor index.toLong() xor 0x52454352L

                val result = runCatching {
                    localDream.generate(
                        ImageGenerationRequest(
                            prompt = built.prompt,
                            negativePrompt = built.negativePrompt,
                            width = built.width,
                            height = built.height,
                            steps = 20,
                            cfgScale = 7.0,
                            seed = seed,
                            cacheKey = "recruit/${candidate.id}/day/${state.currentDay}/portrait",
                            referenceImageBytes = null,
                        ),
                    )
                }.getOrNull()

                if (batchId != recruitPreviewBatch || selectedLocation.value?.id != location.id) return@launch
                if (result != null) {
                    val frameId = "candidate-preview-${state.currentDay}"
                    val relativePath = galleryStore.savePng(candidate.id, frameId, result.bytes)
                    val createdAt = System.currentTimeMillis()
                    candidatePreviewMeta[candidate.id] = RecruitPreviewMeta(
                        relativePath = relativePath,
                        prompt = built.prompt,
                        negativePrompt = built.negativePrompt,
                        seed = result.seed ?: seed,
                        width = result.width.takeIf { it > 0 } ?: built.width,
                        height = result.height.takeIf { it > 0 } ?: built.height,
                        profileRevision = profile.revision,
                        day = state.currentDay,
                        createdAtEpochMs = createdAt,
                    )
                    candidatePortraits.value = candidatePortraits.value +
                        (candidate.id to galleryStore.absolutePath(relativePath))
                    successCount++
                }
            }

            if (batchId == recruitPreviewBatch && selectedLocation.value?.id == location.id) {
                generatingCandidateId.value = null
                uiMessage.value = when (successCount) {
                    batchCandidates.size -> "Все портреты кандидаток готовы."
                    0 -> "Портреты не удалось получить; данные кандидаток доступны без изображений."
                    else -> "Готово портретов: $successCount/${batchCandidates.size}."
                }
            }
        }
    }

    private fun RecruitCandidate.asPreviewStaff(): StaffMember = StaffMember(
        id = id,
        name = name,
        species = species,
        ageYears = ageYears,
        loyalty = startingLoyalty,
        traits = traits,
        skills = skills,
        preferences = preferences,
    )

    private fun hire(candidate: RecruitCandidate) {
        val current = gameState.value ?: return
        val preview = candidatePreviewMeta[candidate.id]
        val result = runCatching { recruitmentEngine.hire(current, candidate) }.getOrElse {
            uiMessage.value = if (it.message?.contains("treasury", true) == true) "Не хватает денег на найм." else it.message
            return
        }
        repository.saveState(result.state)
        repository.appendWorldEvent(result.event)
        val profile = VisualIdentityFactory.fromCandidate(candidate, result.state.worldSeed, result.state.currentDay)
        repository.saveVisualProfile(profile)

        if (preview != null) {
            repository.saveGalleryFrame(
                GalleryFrame(
                    id = "frame-${preview.day}-${candidate.id}-portrait-recruit",
                    staffId = candidate.id,
                    day = preview.day,
                    role = GalleryFrameRole.PORTRAIT,
                    localPath = preview.relativePath,
                    prompt = preview.prompt,
                    negativePrompt = preview.negativePrompt,
                    seed = preview.seed,
                    width = preview.width,
                    height = preview.height,
                    referenceFrameId = null,
                    profileRevision = preview.profileRevision,
                    createdAtEpochMs = preview.createdAtEpochMs,
                    canonical = false,
                ),
            )
        }

        gameState.value = result.state
        recentEvents.value = loadGameplayEvents()
        clearRecruitPreviewState()
        selectedLocation.value = null
        candidates.value = emptyList()
        uiMessage.value = if (preview != null)
            "${candidate.name} теперь работает у вас. Её портрет из найма сохранён в галерее; эталон выберите вручную."
        else "${candidate.name} теперь работает у вас."
        refreshVisualMemory()
    }

    private suspend fun generateAutomaticDayScene(result: DayResult) {
        try {
            val staffReport = featuredStaffReport(result) ?: return
            val member = result.state.staff.firstOrNull { it.id == staffReport.staffId } ?: return
            val profile = repository.visualProfile(member.id)
                ?: VisualIdentityFactory.fromStaff(member, result.state.worldSeed, result.report.day).also(repository::saveVisualProfile)
            val ordinal = repository.galleryForStaff(member.id).size
            val plan = ScenePromptPlanner.day(member.id, result.report.day, ordinal).asPrompt()
            val scene = listOf(plan, buildWorkSceneTags(staffReport, member)).filter { it.isNotBlank() }.joinToString(", ")
            val built = VisualPromptBuilder.build(member, profile, GalleryFrameRole.EVENT, scene, member.inventory)
            val sceneHash = scene.hashCode().toLong() and 0xffffffffL
            val seed = result.state.worldSeed xor member.id.hashCode().toLong() xor
                (result.report.day.toLong() shl 24) xor (ordinal.toLong() shl 8) xor sceneHash
            val generated = localDream.generate(
                ImageGenerationRequest(
                    prompt = built.prompt,
                    negativePrompt = built.negativePrompt,
                    width = built.width,
                    height = built.height,
                    steps = 24,
                    cfgScale = 7.0,
                    seed = seed,
                    cacheKey = "staff/${member.id}/event/day/${result.report.day}/$ordinal/$sceneHash",
                    referenceImageBytes = null,
                ),
            ) ?: return

            val frameId = "frame-${result.report.day}-${member.id}-event-auto-${ordinal + 1}"
            val relativePath = galleryStore.savePng(member.id, frameId, generated.bytes)
            val frame = GalleryFrame(
                id = frameId,
                staffId = member.id,
                day = result.report.day,
                role = GalleryFrameRole.EVENT,
                localPath = relativePath,
                prompt = built.prompt,
                negativePrompt = built.negativePrompt,
                seed = generated.seed ?: seed,
                width = generated.width.takeIf { it > 0 } ?: built.width,
                height = generated.height.takeIf { it > 0 } ?: built.height,
                referenceFrameId = null,
                profileRevision = profile.revision,
                createdAtEpochMs = System.currentTimeMillis(),
                canonical = false,
            )
            repository.saveGalleryFrame(frame)
            refreshVisualMemory()
            homeDayScene.value = UiGalleryFrame(frame, galleryStore.absolutePath(relativePath))
        } catch (_: Throwable) {
            // UI keeps the previous day's scene and reports generation failure after both jobs finish.
        } finally {
            daySceneLoading.value = false
        }
    }

    private fun featuredStaffReport(result: DayResult): StaffDayReport? = result.report.staffReports.maxByOrNull { report ->
        val notableWeight = report.encounters.maxOfOrNull { encounterWeight(it.outcome) } ?: 0
        val realIncident = report.encounters.any { it.outcome == EncounterOutcome.INCIDENT }
        (if (realIncident) 100_000 else 0) +
            (if (report.encounters.isNotEmpty()) 50_000 else 0) +
            notableWeight * 5_000 +
            report.encounters.size * 1_000 +
            (if (report.purchase != null) 500 else 0) +
            report.businessRevenue.coerceAtMost(9_999).toInt()
    }

    private fun buildWorkSceneTags(staffReport: StaffDayReport, member: StaffMember): String {
        val notable = staffReport.encounters.maxByOrNull { encounterWeight(it.outcome) }
        val note = staffReport.incident.orEmpty().lowercase()
        val result = mutableListOf<String>()
        result += "completed day ${staffReport.day}"
        result += "erotic brothel atmosphere"

        when {
            staffReport.encounters.isEmpty() && note.contains("обучен") -> {
                result += listOf(
                    "adult woman training inside brothel",
                    "solo",
                    "sensual skill practice",
                    "training notes and practice props",
                    "focused alluring expression",
                    "no client present",
                )
            }
            staffReport.encounters.isEmpty() && (note.contains("отдых") || note.contains("восстанов")) -> {
                result += listOf(
                    "adult woman resting inside brothel",
                    "solo",
                    "sensual recovery scene",
                    "relaxed private room",
                    "loosened work outfit",
                    "no client present",
                )
            }
            notable?.outcome == EncounterOutcome.INCIDENT || notable?.outcome == EncounterOutcome.REFUSED -> {
                result += listOf(
                    "adult woman after difficult client encounter",
                    "solo",
                    "private decompression after work",
                    "tense sensual aftermath",
                    "no client present",
                )
            }
            notable != null -> {
                result += "adult woman at work"
                result += "adult client present"
                result += "consensual adult professional interaction"
                result += when (notable.serviceCode) {
                    "conversation" -> "intimate conversation with adult client, seated close together, flirtatious professional interaction"
                    "massage" -> "sensual massage service with adult client, massage table, oils and towels, professional erotic work"
                    "roleplay" -> "playful roleplay service with adult client, costume elements, theatrical sensual interaction"
                    "private_intimacy" -> "private intimate service with adult client, close sensual embrace, implied intimacy, non-graphic erotic work"
                    "arcane_fantasy" -> "sensual arcane fantasy service with adult client, magical glow, intimate ritual atmosphere"
                    else -> "sensual professional brothel service with adult client"
                }
                result += when (notable.outcome) {
                    EncounterOutcome.EXCELLENT -> "successful service, confident satisfied mood"
                    EncounterOutcome.GOOD -> "good service, warm flirtatious mood"
                    EncounterOutcome.ROUTINE -> "routine professional service, intimate atmosphere"
                    EncounterOutcome.AWKWARD -> "slightly awkward service, restrained sensual tension"
                    EncounterOutcome.REFUSED, EncounterOutcome.INCIDENT -> "post-service aftermath"
                }
            }
            else -> result += listOf("adult woman", "solo", "quiet day inside brothel", "sensual private moment")
        }

        when {
            member.fatigue >= 75 -> result += "visibly tired, relaxed post-shift body language"
            member.stress >= 70 -> result += "tense expression, private intimate mood"
            else -> result += "composed sensual expression"
        }
        if (staffReport.businessRevenue > 0) result += "coins and signs of completed work nearby"
        staffReport.purchase?.let { result += "new personal purchase nearby" }
        return result.joinToString(", ")
    }

    private fun encounterWeight(outcome: EncounterOutcome): Int = when (outcome) {
        EncounterOutcome.INCIDENT -> 6
        EncounterOutcome.EXCELLENT -> 5
        EncounterOutcome.GOOD -> 4
        EncounterOutcome.ROUTINE -> 3
        EncounterOutcome.AWKWARD -> 2
        EncounterOutcome.REFUSED -> 1
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
        uiMessage.value = "Создаётся новый портрет для ${member.name}…"
        lifecycleScope.launch {
            try {
                val ordinal = repository.galleryForStaff(staffId).size
                val built = VisualPromptBuilder.build(member, profile, role, requestedScene, member.inventory)
                val sceneHash = requestedScene.hashCode().toLong() and 0xffffffffL
                val seed = state.worldSeed xor staffId.hashCode().toLong() xor
                    (generationDay.toLong() shl 24) xor (ordinal.toLong() shl 8) xor role.ordinal.toLong() xor sceneHash
                val cacheKey = "staff/$staffId/${role.name.lowercase()}/day/$generationDay/$ordinal/$sceneHash"
                val result = localDream.generate(
                    ImageGenerationRequest(
                        prompt = built.prompt,
                        negativePrompt = built.negativePrompt,
                        width = built.width,
                        height = built.height,
                        steps = 20,
                        cfgScale = 7.0,
                        seed = seed,
                        cacheKey = cacheKey,
                        referenceImageBytes = null,
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

private data class RecruitPreviewMeta(
    val relativePath: String,
    val prompt: String,
    val negativePrompt: String,
    val seed: Long?,
    val width: Int,
    val height: Int,
    val profileRevision: Int,
    val day: Int,
    val createdAtEpochMs: Long,
)
