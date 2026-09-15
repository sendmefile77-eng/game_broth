package com.sendmefile77.gamebroth

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: SqliteGameRepository
    private lateinit var galleryStore: GalleryFileStore
    private val dayEngine = DayEngine()
    private val recruitmentEngine = RecruitmentEngine()

    private var gameState by mutableStateOf<GameState?>(null)
    private var recentEvents by mutableStateOf<List<WorldEvent>>(emptyList())
    private var latestReport by mutableStateOf<DailyReport?>(null)
    private var dailyNarrative by mutableStateOf<String?>(null)
    private var diaries by mutableStateOf<Map<String, String>>(emptyMap())
    private var locations by mutableStateOf<List<RecruitmentLocation>>(emptyList())
    private var selectedLocation by mutableStateOf<RecruitmentLocation?>(null)
    private var candidates by mutableStateOf<List<RecruitCandidate>>(emptyList())
    private var visualProfiles by mutableStateOf<Map<String, VisualIdentityProfile>>(emptyMap())
    private var galleries by mutableStateOf<Map<String, List<GalleryUiFrame>>>(emptyMap())
    private var generatingStaffId by mutableStateOf<String?>(null)
    private var uiMessage by mutableStateOf<String?>(null)
    private var tellamaStatus by mutableStateOf("не проверено")
    private var localDreamStatus by mutableStateOf("не проверено")
    private var apiKey by mutableStateOf("")
    private lateinit var tellama: TellamaClient
    private lateinit var localDream: LocalDreamClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SqliteGameRepository(this)
        galleryStore = GalleryFileStore(this)
        val prefs = getSharedPreferences(PREFS_AI, MODE_PRIVATE)
        apiKey = prefs.getString(KEY_TELLAMA_API_KEY, "").orEmpty()
        tellama = TellamaClient(apiKeyProvider = { apiKey })
        localDream = LocalDreamClient()
        gameState = repository.loadOrCreate()
        recentEvents = repository.recentWorldEvents(8)
        latestReport = repository.latestDailyReport()
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()

        setContent {
            MaterialTheme {
                GameBrothScreen(
                    state = gameState,
                    events = recentEvents,
                    report = latestReport,
                    narrative = dailyNarrative,
                    diaries = diaries,
                    locations = locations,
                    selectedLocation = selectedLocation,
                    candidates = candidates,
                    visualProfiles = visualProfiles,
                    galleries = galleries,
                    generatingStaffId = generatingStaffId,
                    uiMessage = uiMessage,
                    tellamaStatus = tellamaStatus,
                    localDreamStatus = localDreamStatus,
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    onSaveApiKey = {
                        prefs.edit().putString(KEY_TELLAMA_API_KEY, apiKey.trim()).apply()
                        tellamaStatus = "ключ сохранён"
                    },
                    onAdvanceDay = ::advanceDay,
                    onCheckAi = ::checkAi,
                    onSelectLocation = ::selectLocation,
                    onBack = { selectedLocation = null; candidates = emptyList() },
                    onHire = ::hire,
                    onGeneratePortrait = { generateStaffFrame(it, GalleryFrameRole.PORTRAIT, "neutral casting pose in a private room of the establishment") },
                    onGenerateStoryFrame = { generateStaffFrame(it, GalleryFrameRole.SCENE, "quiet after-work character scene in her room, showing current accessories and clothing") },
                    onMakeCanonical = ::makeCanonical,
                    onToggleIdentityLock = ::toggleIdentityLock,
                )
            }
        }
    }

    private fun advanceDay() {
        val current = gameState ?: return
        val result = dayEngine.advanceDay(current)
        repository.saveState(result.state)
        repository.saveDailyReport(result.report)
        result.events.forEach(repository::appendWorldEvent)
        result.memories.forEach(repository::appendMemory)
        gameState = result.state
        latestReport = result.report
        dailyNarrative = null
        recentEvents = repository.recentWorldEvents(8)
        selectedLocation = null
        candidates = emptyList()
        uiMessage = "День ${result.report.day} закрыт. Отчёт сформирован и сохранён."
        refreshDiaries()
        refreshRecruitment()
        refreshVisualMemory()
        narrate(result)
    }

    private fun narrate(result: DayResult) {
        if (apiKey.isBlank()) return
        lifecycleScope.launch {
            val facts = buildString {
                result.report.staffReports.forEach { sr ->
                    append(sr.staffName).append(" level ").append(sr.levelBefore).append("->").append(sr.levelAfter).append('\n')
                    sr.encounters.forEachIndexed { i, e ->
                        append("client ").append(i + 1).append(": ").append(e.client.displayName).append(" / ")
                            .append(serviceLabel(e.serviceCode)).append(" / ").append(e.outcome.name).append(" / ").append(e.summary).append('\n')
                    }
                    sr.purchase?.let { append("purchase: ").append(it.item.name).append(" for ").append(it.price).append('\n') }
                }
                append("treasuryAfter=").append(result.report.treasuryAfter).append(" upkeep=").append(result.report.upkeep).append(" debtDelta=").append(result.report.debtDelta)
            }
            val out = runCatching {
                tellama.generate(
                    TextGenerationRequest(
                        systemPrompt = "Все персонажи совершеннолетние. Перепиши только переданные факты живым русским текстом. Каждый клиент — отдельный короткий абзац. Не меняй числа, исходы и личные границы. Интимные события описывай без графических анатомических подробностей. Немного чёрного юмора допустимо.",
                        stateDigest = GameStateDigest.from(result.state),
                        playerAction = facts,
                        recentEvents = result.events,
                        maxTokens = 700,
                        temperature = .72,
                    ),
                )
            }.getOrNull()
            out?.content?.let { dailyNarrative = it }
        }
    }

    private fun refreshRecruitment() { gameState?.let { locations = recruitmentEngine.locations(it) } }

    private fun refreshDiaries() {
        gameState?.let { state ->
            diaries = state.staff.mapNotNull { member ->
                repository.memoriesForStaff(member.id, 30).firstOrNull { it.category == "diary" }?.let { member.id to it.summary }
            }.toMap()
        }
    }

    private fun refreshVisualMemory() {
        val state = gameState ?: return
        val profileMap = mutableMapOf<String, VisualIdentityProfile>()
        val galleryMap = mutableMapOf<String, List<GalleryUiFrame>>()
        state.staff.forEach { member ->
            val profile = repository.visualProfile(member.id) ?: VisualIdentityFactory.fromStaff(member, state.worldSeed, state.currentDay).also(repository::saveVisualProfile)
            profileMap[member.id] = profile
            galleryMap[member.id] = repository.galleryForStaff(member.id).map { GalleryUiFrame(it, galleryStore.absolutePath(it.localPath)) }
        }
        visualProfiles = profileMap
        galleries = galleryMap
    }

    private fun selectLocation(location: RecruitmentLocation) {
        gameState?.let {
            selectedLocation = location
            candidates = recruitmentEngine.candidates(it, location)
            uiMessage = null
        }
    }

    private fun hire(candidate: RecruitCandidate) {
        val current = gameState ?: return
        val result = runCatching { recruitmentEngine.hire(current, candidate) }.getOrElse {
            uiMessage = if (it.message?.contains("treasury", true) == true) "Не хватает денег на найм." else it.message
            return
        }
        repository.saveState(result.state)
        repository.appendWorldEvent(result.event)
        repository.saveVisualProfile(VisualIdentityFactory.fromCandidate(candidate, result.state.worldSeed, result.state.currentDay))
        gameState = result.state
        recentEvents = repository.recentWorldEvents(8)
        selectedLocation = null
        candidates = emptyList()
        uiMessage = "${candidate.name} нанята. Её статы и канон внешности сохранены."
        refreshVisualMemory()
    }

    private fun generateStaffFrame(staffId: String, role: GalleryFrameRole, scene: String) {
        val state = gameState ?: return
        val member = state.staff.firstOrNull { it.id == staffId } ?: return
        val profile = repository.visualProfile(staffId) ?: VisualIdentityFactory.fromStaff(member, state.worldSeed, state.currentDay).also(repository::saveVisualProfile)
        if (generatingStaffId != null) {
            uiMessage = "Local Dream уже генерирует другой кадр."
            return
        }
        generatingStaffId = staffId
        uiMessage = "Генерируется кадр для ${member.name}…"
        lifecycleScope.launch {
            try {
                val canonicalFrame = profile.canonicalFrameId?.let(repository::galleryFrame)
                val referenceBytes = canonicalFrame?.let { galleryStore.read(it.localPath) }
                val built = VisualPromptBuilder.build(member, profile, role, scene, member.inventory)
                val ordinal = repository.galleryForStaff(staffId).size
                val seed = state.worldSeed xor staffId.hashCode().toLong() xor (state.currentDay.toLong() shl 24) xor (ordinal.toLong() shl 8) xor role.ordinal.toLong()
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
                        referenceStrength = if (referenceBytes == null) 0.62 else 0.54,
                    ),
                ) ?: error("Local Dream не вернула изображение")
                val frameId = "frame-${state.currentDay}-${staffId}-${ordinal + 1}"
                val relativePath = galleryStore.savePng(staffId, frameId, result.bytes)
                val firstCanonical = profile.canonicalFrameId == null && role == GalleryFrameRole.PORTRAIT
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
                    canonical = firstCanonical,
                )
                repository.saveGalleryFrame(frame)
                if (firstCanonical) repository.setCanonicalFrame(staffId, frame.id)
                refreshVisualMemory()
                uiMessage = if (firstCanonical) "Портрет ${member.name} сохранён в галерею и стал первым эталоном." else "Кадр ${member.name} сохранён в её галерею."
            } catch (error: Throwable) {
                uiMessage = "Изображение не получено: ${error.message ?: error::class.java.simpleName}"
            } finally {
                generatingStaffId = null
            }
        }
    }

    private fun makeCanonical(staffId: String, frameId: String) {
        val updated = repository.setCanonicalFrame(staffId, frameId)
        uiMessage = if (updated == null) "Не удалось назначить эталон." else "Новый эталон внешности сохранён. Следующие кадры будут использовать его как reference."
        refreshVisualMemory()
    }

    private fun toggleIdentityLock(staffId: String) {
        val current = repository.visualProfile(staffId) ?: return
        repository.setVisualIdentityLocked(staffId, !current.locked)
        uiMessage = if (current.locked) "Внешность разблокирована для ручной правки." else "Канон внешности закреплён."
        refreshVisualMemory()
    }

    private fun checkAi() {
        lifecycleScope.launch {
            tellamaStatus = "проверяем…"
            localDreamStatus = "проверяем…"
            val t = tellama.status(true)
            tellamaStatus = if (t.available) "готово: ${t.model}" else "недоступно: ${t.detail}"
            val i = localDream.status(true)
            localDreamStatus = if (i.available) "готово" else "недоступно: ${i.detail}"
        }
    }

    companion object {
        const val PREFS_AI = "ai_settings"
        const val KEY_TELLAMA_API_KEY = "tellama_api_key"
    }
}

data class GalleryUiFrame(val frame: GalleryFrame, val absolutePath: String)

@Composable
private fun GameBrothScreen(
    state: GameState?,
    events: List<WorldEvent>,
    report: DailyReport?,
    narrative: String?,
    diaries: Map<String, String>,
    locations: List<RecruitmentLocation>,
    selectedLocation: RecruitmentLocation?,
    candidates: List<RecruitCandidate>,
    visualProfiles: Map<String, VisualIdentityProfile>,
    galleries: Map<String, List<GalleryUiFrame>>,
    generatingStaffId: String?,
    uiMessage: String?,
    tellamaStatus: String,
    localDreamStatus: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onAdvanceDay: () -> Unit,
    onCheckAi: () -> Unit,
    onSelectLocation: (RecruitmentLocation) -> Unit,
    onBack: () -> Unit,
    onHire: (RecruitCandidate) -> Unit,
    onGeneratePortrait: (String) -> Unit,
    onGenerateStoryFrame: (String) -> Unit,
    onMakeCanonical: (String, String) -> Unit,
    onToggleIdentityLock: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GAME BROTH", style = MaterialTheme.typography.headlineMedium)
            Text("M2 / 0.3.0 · SQLite + визуальная память + персональные галереи")
            if (state == null) { Text("Загрузка…"); return@Column }
            val e = state.establishment
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleLarge)
                    Text("День ${state.currentDay} · казна ${e.treasury} · долг ${e.debt}")
                    Text("Репутация ${e.publicReputation} · внимание ${e.heat} · персонал ${state.staff.size}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdvanceDay) { Text("Закрыть день") }
                Button(onClick = onCheckAi) { Text("Проверить ИИ") }
            }
            uiMessage?.let { Text(it) }
            report?.let { DailyReportCard(it, narrative) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Поиск персонала", style = MaterialTheme.typography.titleMedium)
                    if (selectedLocation == null) {
                        locations.forEach { l ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(l.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${l.district} · риск ${l.risk}/100")
                                    Button(onClick = { onSelectLocation(l) }) { Text("Идти сюда") }
                                }
                            }
                        }
                    } else {
                        Text(selectedLocation.name, style = MaterialTheme.typography.titleSmall)
                        candidates.forEach { c ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("${c.name}, ${c.ageYears} · ${c.species}")
                                    Text(c.profileHook)
                                    Text("Навыки: ${c.skills.values.joinToString { "${skillLabel(it.code)} ${it.level}" }}")
                                    Text("Любит: ${c.preferences.filterValues { it == PreferenceStance.ENJOY }.keys.joinToString { serviceLabel(it) }}")
                                    Text("Жёсткая граница: ${c.preferences.filterValues { it == PreferenceStance.HARD_LIMIT }.keys.joinToString { serviceLabel(it) }}")
                                    Text("Найм: ${c.signingFee}")
                                    Button(onClick = { onHire(c) }) { Text("Нанять") }
                                }
                            }
                        }
                        TextButton(onClick = onBack) { Text("Назад") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Персонал · память · галереи", style = MaterialTheme.typography.titleMedium)
                    Text("Статы, границы и visual identity хранятся отдельно от LLM. Каждый кадр сохраняется в галерею сотрудницы.")
                    state.staff.forEach { member ->
                        StaffVisualCard(
                            member = member,
                            diary = diaries[member.id],
                            profile = visualProfiles[member.id],
                            frames = galleries[member.id].orEmpty(),
                            generating = generatingStaffId == member.id,
                            onGeneratePortrait = { onGeneratePortrait(member.id) },
                            onGenerateStoryFrame = { onGenerateStoryFrame(member.id) },
                            onMakeCanonical = { frameId -> onMakeCanonical(member.id, frameId) },
                            onToggleIdentityLock = { onToggleIdentityLock(member.id) },
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Локальные модели", style = MaterialTheme.typography.titleMedium)
                    Text("Tellama/Qwen: $tellamaStatus")
                    Text("Local Dream: $localDreamStatus")
                    OutlinedTextField(apiKey, onApiKeyChange, label = { Text("API-ключ Tellama") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onSaveApiKey) { Text("Сохранить ключ") }
                }
            }
            if (events.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Последние события", style = MaterialTheme.typography.titleMedium)
                        events.forEach { Text("Д${it.day}: ${it.summary}") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StaffVisualCard(
    member: StaffMember,
    diary: String?,
    profile: VisualIdentityProfile?,
    frames: List<GalleryUiFrame>,
    generating: Boolean,
    onGeneratePortrait: () -> Unit,
    onGenerateStoryFrame: () -> Unit,
    onMakeCanonical: (String) -> Unit,
    onToggleIdentityLock: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${member.name} · ур.${member.level} · ${member.species}", style = MaterialTheme.typography.titleSmall)
            Text("Здоровье ${member.health} · усталость ${member.fatigue} · стресс ${member.stress} · лояльность ${member.loyalty}")
            Text("Личные деньги ${member.personalMoney} · вещей ${member.inventory.size}")
            diary?.let { Text("Дневник: $it") }
            profile?.let {
                Text("Visual ID r${it.revision} · ${if (it.locked) "🔒 закреплена" else "🔓 редактируема"}")
                Text("${it.build}; ${it.skinTone}; ${it.hair}; ${it.eyes}; ${it.face}")
                if (it.distinctiveMarks.isNotEmpty()) Text("Маркеры: ${it.distinctiveMarks.joinToString()}")
                TextButton(onClick = onToggleIdentityLock) { Text(if (it.locked) "Разблокировать внешность" else "Закрепить внешность") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGeneratePortrait, enabled = !generating) { Text(if (generating) "Генерация…" else "Новый портрет") }
                OutlinedButton(onClick = onGenerateStoryFrame, enabled = !generating) { Text("Кадр дня") }
            }
            Text("Галерея: ${frames.size} кадров${profile?.canonicalFrameId?.let { " · эталон сохранён" }.orEmpty()}")
            frames.take(6).forEach { ui -> GalleryFrameCard(ui, onMakeCanonical) }
        }
    }
}

@Composable
private fun GalleryFrameCard(ui: GalleryUiFrame, onMakeCanonical: (String) -> Unit) {
    val bitmap = remember(ui.absolutePath) { runCatching { BitmapFactory.decodeFile(ui.absolutePath)?.asImageBitmap() }.getOrNull() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            bitmap?.let {
                Image(it, contentDescription = "Gallery frame", modifier = Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit)
            }
            Text("День ${ui.frame.day} · ${ui.frame.role.name}${if (ui.frame.canonical) " · ⭐ ЭТАЛОН" else ""}")
            Text("Visual r${ui.frame.profileRevision}${ui.frame.referenceFrameId?.let { " · reference: $it" }.orEmpty()}")
            if (!ui.frame.canonical) TextButton(onClick = { onMakeCanonical(ui.frame.id) }) { Text("Сделать эталонным") }
        }
    }
}

@Composable
private fun DailyReportCard(report: DailyReport, narrative: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥 Ежедневный отчёт · день ${report.day}", style = MaterialTheme.typography.titleMedium)
            report.staffReports.forEach { sr ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${sr.staffName} · ур. ${sr.levelBefore} → ${sr.levelAfter}", style = MaterialTheme.typography.titleSmall)
                        if (sr.encounters.isEmpty()) Text(sr.incident ?: "Клиентов не было") else sr.encounters.forEachIndexed { i, e ->
                            Text("Клиент ${i + 1}: ${e.client.displayName} (${e.client.archetype})")
                            Text("${serviceLabel(e.serviceCode)} · ${outcomeLabel(e.outcome)} — ${e.summary}")
                            Text("Доход ${e.grossRevenue}: заведению ${e.businessCut}, сотруднице ${e.staffCut}")
                        }
                        sr.incident?.let { Text("Инцидент: $it") }
                        sr.purchase?.let { Text("Покупка: ${it.item.name} за ${it.price}") }
                        Text("Итого заведению: ${sr.businessRevenue}; личная доля: ${sr.personalRevenue}")
                    }
                }
            }
            Text("💰 Казна: ${report.treasuryAfter} · расходы ${report.upkeep} · новый долг ${report.debtDelta}")
            narrative?.let { Text("Qwen-версия", style = MaterialTheme.typography.titleSmall); Text(it) }
        }
    }
}

private fun serviceLabel(c: String) = when (c) {
    "conversation" -> "Компания и разговор"
    "massage" -> "Массаж"
    "roleplay" -> "Ролевая услуга"
    "private_intimacy" -> "Приватная близость"
    "arcane_fantasy" -> "Магическая фантазия"
    else -> c
}

private fun skillLabel(c: String) = when (c) {
    "hospitality" -> "сервис"
    "social" -> "общение"
    "bodywork" -> "телесная работа"
    "roleplay" -> "роли"
    "intimacy" -> "интимность"
    "arcane" -> "магия"
    else -> c
}

private fun outcomeLabel(o: EncounterOutcome) = when (o) {
    EncounterOutcome.EXCELLENT -> "отлично"
    EncounterOutcome.GOOD -> "хорошо"
    EncounterOutcome.ROUTINE -> "обычно"
    EncounterOutcome.AWKWARD -> "неловко"
    EncounterOutcome.REFUSED -> "отказ"
    EncounterOutcome.INCIDENT -> "инцидент"
}
