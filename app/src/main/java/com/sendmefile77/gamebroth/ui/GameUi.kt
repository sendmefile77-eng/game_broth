package com.sendmefile77.gamebroth.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sendmefile77.gamebroth.model.*
import com.sendmefile77.gamebroth.simulation.RecruitCandidate
import com.sendmefile77.gamebroth.simulation.RecruitmentLocation

private val Coal = Color(0xFF0C0D0E)
private val Graphite = Color(0xFF151719)
private val Raised = Color(0xFF1E2022)
private val Wine = Color(0xFF692D35)
private val WineDeep = Color(0xFF431C22)
private val Bronze = Color(0xFF9A7A4D)
private val Ivory = Color(0xFFE7DDC9)
private val Muted = Color(0xFFAAA293)
private val Danger = Color(0xFFB45858)

private val BrothelColors = darkColorScheme(
    primary = Bronze,
    onPrimary = Coal,
    secondary = Wine,
    onSecondary = Ivory,
    background = Coal,
    onBackground = Ivory,
    surface = Graphite,
    onSurface = Ivory,
    surfaceVariant = Raised,
    onSurfaceVariant = Muted,
    error = Danger,
)

private val BrothelShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

data class UiGalleryFrame(val frame: GalleryFrame, val absolutePath: String)

private enum class Page { HOME, PLAN, MANAGEMENT, REQUESTS, RECRUIT, STAFF, STAFF_DETAIL, REPORT, SETTINGS }
private enum class StaffTab { ABOUT, SKILLS, DIARY, GALLERY }

@Composable
fun GameBrothUi(
    state: GameState?,
    events: List<WorldEvent>,
    report: DailyReport?,
    narrative: String?,
    narrativeLoading: Boolean,
    homeDayScene: UiGalleryFrame?,
    daySceneLoading: Boolean,
    dayProcessing: Boolean,
    diaries: Map<String, String>,
    staffRequests: List<StaffRequest>,
    locations: List<RecruitmentLocation>,
    selectedLocation: RecruitmentLocation?,
    candidates: List<RecruitCandidate>,
    candidatePortraits: Map<String, String>,
    candidatePortraitErrors: Map<String, String>,
    generatingCandidateId: String?,
    visualProfiles: Map<String, VisualIdentityProfile>,
    galleries: Map<String, List<UiGalleryFrame>>,
    generatingStaffId: String?,
    uiMessage: String?,
    tellamaStatus: String,
    localDreamStatus: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onAdvanceDay: () -> Unit,
    onSetStaffPlan: (String, StaffStatus) -> Unit,
    onResolveStaffRequest: (String, Boolean) -> Unit,
    onSetPricing: (PricingPolicy) -> Unit,
    onSetWorkload: (WorkloadPolicy) -> Unit,
    onRepayDebt: () -> Unit,
    onUpgradeLuxury: () -> Unit,
    onUpgradeSecrecy: () -> Unit,
    onLayLow: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCheckAi: () -> Unit,
    onSelectLocation: (RecruitmentLocation) -> Unit,
    onRecruitBack: () -> Unit,
    onHire: (RecruitCandidate) -> Unit,
    onGeneratePortrait: (String) -> Unit,
    onMakeCanonical: (String, String) -> Unit,
    onToggleIdentityLock: (String) -> Unit,
) {
    val imageBackendContext = androidx.compose.ui.platform.LocalContext.current
    com.sendmefile77.gamebroth.ai.ImageBackendConfig.initialize(imageBackendContext)
    com.sendmefile77.gamebroth.ai.TextBackendConfig.initialize(imageBackendContext)

    MaterialTheme(colorScheme = BrothelColors, shapes = BrothelShapes) {
        var pageName by rememberSaveable { mutableStateOf(Page.HOME.name) }
        var selectedStaffId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
        val page = runCatching { Page.valueOf(pageName) }.getOrDefault(Page.HOME)
        val navigate: (Page) -> Unit = { pageName = it.name }

        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Coal) {
            if (state == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Зажигаем свечи…", color = Muted)
                }
            } else {
                when (page) {
                    Page.HOME -> HomeScreen(
                        state = state,
                        events = events,
                        reportDay = report?.day,
                        narrative = narrative,
                        narrativeLoading = narrativeLoading,
                        uiMessage = uiMessage,
                        heroFrame = homeDayScene,
                        heroLoading = daySceneLoading,
                        dayProcessing = dayProcessing,
                        requestCount = staffRequests.size,
                        onRecruit = { selectedCandidateId = null; navigate(Page.RECRUIT) },
                        onStaff = { navigate(Page.STAFF) },
                        onPlan = { navigate(Page.PLAN) },
                        onManagement = { navigate(Page.MANAGEMENT) },
                        onRequests = { navigate(Page.REQUESTS) },
                        onChronicle = { if (report != null) navigate(Page.REPORT) },
                        onCloseDay = onAdvanceDay,
                        onSettings = { navigate(Page.SETTINGS) },
                    )

                    Page.PLAN -> DayPlanScreen(
                        state = state,
                        uiMessage = uiMessage,
                        onBack = { navigate(Page.HOME) },
                        onSetPlan = onSetStaffPlan,
                    )

                    Page.MANAGEMENT -> ManagementScreen(
                        state = state,
                        uiMessage = uiMessage,
                        dayProcessing = dayProcessing,
                        onBack = { navigate(Page.HOME) },
                        onSetPricing = onSetPricing,
                        onSetWorkload = onSetWorkload,
                        onRepayDebt = onRepayDebt,
                        onUpgradeLuxury = onUpgradeLuxury,
                        onUpgradeSecrecy = onUpgradeSecrecy,
                        onLayLow = onLayLow,
                    )

                    Page.REQUESTS -> StaffRequestsScreen(
                        state = state,
                        requests = staffRequests,
                        uiMessage = uiMessage,
                        onBack = { navigate(Page.HOME) },
                        onResolve = onResolveStaffRequest,
                    )

                    Page.RECRUIT -> RecruitmentScreen(
                        state = state,
                        locations = locations,
                        selectedLocation = selectedLocation,
                        candidates = candidates,
                        candidatePortraits = candidatePortraits,
                        candidatePortraitErrors = candidatePortraitErrors,
                        generatingCandidateId = generatingCandidateId,
                        selectedCandidateId = selectedCandidateId,
                        uiMessage = uiMessage,
                        onBack = {
                            when {
                                selectedCandidateId != null -> selectedCandidateId = null
                                selectedLocation != null -> onRecruitBack()
                                else -> navigate(Page.HOME)
                            }
                        },
                        onSelectLocation = onSelectLocation,
                        onOpenCandidate = { selectedCandidateId = it.id },
                        onHire = { candidate ->
                            onHire(candidate)
                            selectedCandidateId = null
                            navigate(Page.STAFF)
                        },
                    )

                    Page.STAFF -> StaffListScreen(
                        state = state,
                        galleries = galleries,
                        visualProfiles = visualProfiles,
                        onBack = { navigate(Page.HOME) },
                        onOpenStaff = {
                            selectedStaffId = it.id
                            navigate(Page.STAFF_DETAIL)
                        },
                    )

                    Page.STAFF_DETAIL -> {
                        val member = state.staff.firstOrNull { it.id == selectedStaffId }
                        if (member == null) navigate(Page.STAFF)
                        else StaffDetailScreen(
                            state = state,
                            member = member,
                            diary = diaries[member.id],
                            profile = visualProfiles[member.id],
                            frames = galleries[member.id].orEmpty(),
                            generating = generatingStaffId == member.id,
                            uiMessage = uiMessage,
                            onBack = { navigate(Page.STAFF) },
                            onGeneratePortrait = { onGeneratePortrait(member.id) },
                            onMakeCanonical = { frameId -> onMakeCanonical(member.id, frameId) },
                        )
                    }

                    Page.REPORT -> ReportScreen(
                        report = report,
                        narrative = narrative,
                        onBack = { navigate(Page.HOME) },
                        onNextMorning = { navigate(Page.HOME) },
                    )

                    Page.SETTINGS -> SettingsScreen(
                        state = state,
                        profiles = visualProfiles,
                        tellamaStatus = tellamaStatus,
                        localDreamStatus = localDreamStatus,
                        apiKey = apiKey,
                        onApiKeyChange = onApiKeyChange,
                        onSaveApiKey = onSaveApiKey,
                        onCheckAi = onCheckAi,
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        onToggleIdentityLock = onToggleIdentityLock,
                        onBack = { navigate(Page.HOME) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: GameState,
    events: List<WorldEvent>,
    reportDay: Int?,
    narrative: String?,
    narrativeLoading: Boolean,
    uiMessage: String?,
    heroFrame: UiGalleryFrame?,
    heroLoading: Boolean,
    dayProcessing: Boolean,
    requestCount: Int,
    onRecruit: () -> Unit,
    onStaff: () -> Unit,
    onPlan: () -> Unit,
    onManagement: () -> Unit,
    onRequests: () -> Unit,
    onChronicle: () -> Unit,
    onCloseDay: () -> Unit,
    onSettings: () -> Unit,
) {
    val activeStaff = state.staff.filter { it.status != StaffStatus.LEFT }
    val working = activeStaff.count { it.status == StaffStatus.AVAILABLE || it.status == StaffStatus.WORKING }
    val resting = activeStaff.count { it.status == StaffStatus.RESTING || it.status == StaffStatus.INJURED }
    val training = activeStaff.count { it.status == StaffStatus.TRAINING }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val heroSubtitle = heroFrame?.let { "Кадр завершённого дня ${it.frame.day}" } ?: "День ${state.currentDay}"
        HeroScene(heroFrame, state, state.establishment.name, heroSubtitle, heroLoading, onSettings)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrothelAction("Искать персонал", onRecruit, Modifier.weight(1f), enabled = !dayProcessing)
                BrothelAction("Персонал", onStaff, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrothelAction("План дня", onPlan, Modifier.weight(1f), enabled = !dayProcessing && activeStaff.isNotEmpty())
                BrothelAction("Заведение", onManagement, Modifier.weight(1f), enabled = !dayProcessing)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrothelAction(
                    if (requestCount > 0) "Просьбы · $requestCount" else "Просьбы",
                    onRequests,
                    Modifier.weight(1f),
                    enabled = !dayProcessing && requestCount > 0,
                    accent = requestCount > 0,
                )
                BrothelAction("Хроника", onChronicle, Modifier.weight(1f), enabled = reportDay != null)
            }
            BrothelAction(
                if (dayProcessing) "Закрываем день…" else "Закончить день",
                onCloseDay,
                Modifier.fillMaxWidth(),
                accent = true,
                enabled = !dayProcessing && activeStaff.isNotEmpty(),
            )

            if (activeStaff.isNotEmpty()) {
                Text("Сегодня: работают $working · отдыхают $resting · учатся $training", color = Muted, fontSize = 13.sp)
            }
            Text(
                "Цены: ${pricingLabel(state.establishment.pricingPolicy)} · нагрузка: ${workloadLabel(state.establishment.workloadPolicy)}",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onManagement),
            )
            if (requestCount > 0) {
                Text(
                    "Персонал ждёт ответа: $requestCount. Неотвеченная просьба после закрытия дня будет воспринята как отказ.",
                    color = Bronze,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onRequests),
                )
            }
            if (state.establishment.debt > 0 || state.establishment.heat > 0) {
                Text(
                    buildString {
                        if (state.establishment.debt > 0) append("Долг ${state.establishment.debt} г.")
                        if (state.establishment.debt > 0 && state.establishment.heat > 0) append(" · ")
                        if (state.establishment.heat > 0) append("Внимание города ${state.establishment.heat}/100")
                    },
                    color = if (state.establishment.heat >= 60) Danger else Bronze,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onManagement),
                )
            }

            uiMessage?.let { MessageStrip(it) }

            if (reportDay != null) Text("Хроника дня $reportDay", color = Bronze, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val homeText = when {
                !narrative.isNullOrBlank() -> narrative
                narrativeLoading -> "Qwen пишет хронику завершённого дня…"
                reportDay != null -> "Подробные механические итоги дня доступны в разделе «Хроника»."
                events.isNotEmpty() -> events.first().summary
                state.staff.isEmpty() -> "Утро пахнет сыростью и дешёвой надеждой. Денег мало, кровать одна, а вывеска звучит куда богаче самого заведения. Пора искать первую сотрудницу."
                else -> "Заведение просыпается. Персонал ждёт распоряжений, город — повода вмешаться."
            }
            Text(homeText, color = Ivory, fontSize = 17.sp, lineHeight = 25.sp)
            if (narrativeLoading && !narrative.isNullOrBlank()) Text("Qwen дописывает более живую версию хроники…", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun HeroScene(
    frame: UiGalleryFrame?,
    state: GameState,
    title: String,
    subtitle: String,
    loading: Boolean,
    onSettings: () -> Unit,
) {
    val generationProgress by com.sendmefile77.gamebroth.ai.ImageGenerationProgressStore.state.collectAsState()
    Box(Modifier.fillMaxWidth().height(470.dp).background(Brush.verticalGradient(listOf(Color(0xFF261B1B), Coal)))) {
        if (frame != null) LocalFrameImage(frame.absolutePath, Modifier.fillMaxSize(), ContentScale.Crop)
        else Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2020), Color(0xFF17191A), Color(0xFF26191B)))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♜", fontSize = 70.sp, color = Bronze.copy(alpha = .75f))
                Text(title, color = Ivory, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("тёмный город ещё не знает вашего имени", color = Muted, fontSize = 14.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent))))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudChip("День ${state.currentDay}")
            Spacer(Modifier.width(7.dp))
            HudChip("${state.establishment.treasury} г.")
            Spacer(Modifier.width(7.dp))
            HudChip("Реп. ${state.establishment.publicReputation}")
            Spacer(Modifier.weight(1f))
            Text("⚙", color = Ivory, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onSettings).padding(8.dp))
        }
        if (loading || generationProgress.running) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(.78f).background(Color.Black.copy(alpha = .78f), RoundedCornerShape(5.dp)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    generationProgress.message.ifBlank { "Генератор создаёт новый кадр дня…" },
                    color = Ivory,
                    fontSize = 14.sp,
                )
                val fraction = generationProgress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Bronze,
                        trackColor = Raised,
                    )
                    generationProgress.counterText?.let { counter ->
                        Text(
                            "$counter · ${generationProgress.width}×${generationProgress.height}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Bronze,
                        trackColor = Raised,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(130.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Coal.copy(alpha = .94f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, color = Ivory, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DayPlanScreen(
    state: GameState,
    uiMessage: String?,
    onBack: () -> Unit,
    onSetPlan: (String, StaffStatus) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("План на день ${state.currentDay}", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkCard(Modifier.fillMaxWidth()) {
                Text("Распоряжения", color = Ivory, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Работа приносит деньги и опыт, но повышает усталость. Отдых восстанавливает силы. Учёба стоит заведению 2 галеона и прокачивает самый слабый навык.", color = Muted, lineHeight = 21.sp)
            }
            uiMessage?.let { MessageStrip(it) }
            state.staff.filter { it.status != StaffStatus.LEFT }.forEach { member ->
                DarkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${member.species} · ур. ${member.level}", color = Muted)
                        }
                        Text(planLabel(member), color = planColor(member), fontWeight = FontWeight.SemiBold)
                    }
                    Text("Здоровье ${member.health} · усталость ${member.fatigue} · стресс ${member.stress}", color = Muted, fontSize = 13.sp)
                    if (member.health < 45 || member.fatigue > 80) Text("Состояние плохое: работа или обучение могут сорваться автоматически.", color = Danger, fontSize = 13.sp)
                    Spacer(Modifier.height(7.dp))
                    if (member.status == StaffStatus.INJURED) {
                        Text("Сегодня назначено восстановление после травмы. Другие распоряжения недоступны.", color = Bronze)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlanChoice("Работать", member.status == StaffStatus.AVAILABLE || member.status == StaffStatus.WORKING, Modifier.weight(1f)) { onSetPlan(member.id, StaffStatus.AVAILABLE) }
                            PlanChoice("Отдых", member.status == StaffStatus.RESTING, Modifier.weight(1f)) { onSetPlan(member.id, StaffStatus.RESTING) }
                            PlanChoice("Учёба", member.status == StaffStatus.TRAINING, Modifier.weight(1f)) { onSetPlan(member.id, StaffStatus.TRAINING) }
                        }
                    }
                }
            }
            if (state.staff.none { it.status != StaffStatus.LEFT }) Text("Сначала наймите хотя бы одну сотрудницу.", color = Muted)
            BrothelAction("Готово", onBack, Modifier.fillMaxWidth(), accent = true)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ManagementScreen(
    state: GameState,
    uiMessage: String?,
    dayProcessing: Boolean,
    onBack: () -> Unit,
    onSetPricing: (PricingPolicy) -> Unit,
    onSetWorkload: (WorkloadPolicy) -> Unit,
    onRepayDebt: () -> Unit,
    onUpgradeLuxury: () -> Unit,
    onUpgradeSecrecy: () -> Unit,
    onLayLow: () -> Unit,
) {
    val e = state.establishment
    val luxuryCost = 6L + (e.luxury / 10) * 3L
    val secrecyCost = 7L + (e.secrecy / 10) * 3L
    val layLowCost = 3L + e.heat / 20L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Заведение", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkCard(Modifier.fillMaxWidth()) {
                Text(e.name, color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Казна ${e.treasury} г. · долг ${e.debt} г.", color = Bronze)
                Text("Репутация ${e.publicReputation} · внимание города ${e.heat}/100", color = if (e.heat >= 60) Danger else Muted)
                Text("Комфорт ${e.luxury}/100 · скрытность ${e.secrecy}/100", color = Muted)
            }
            uiMessage?.let { MessageStrip(it) }

            Text("Ценовая политика", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyChoice("Доступно", e.pricingPolicy == PricingPolicy.BUDGET, Modifier.weight(1f), !dayProcessing) { onSetPricing(PricingPolicy.BUDGET) }
                PolicyChoice("Обычно", e.pricingPolicy == PricingPolicy.STANDARD, Modifier.weight(1f), !dayProcessing) { onSetPricing(PricingPolicy.STANDARD) }
                PolicyChoice("Премиум", e.pricingPolicy == PricingPolicy.PREMIUM, Modifier.weight(1f), !dayProcessing) { onSetPricing(PricingPolicy.PREMIUM) }
            }
            Text("Доступные цены немного уменьшают маржу и помогают репутации. Премиум повышает маржу; высокий комфорт делает его особенно полезным.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)

            Text("Нагрузка", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyChoice("Бережно", e.workloadPolicy == WorkloadPolicy.GENTLE, Modifier.weight(1f), !dayProcessing) { onSetWorkload(WorkloadPolicy.GENTLE) }
                PolicyChoice("Обычно", e.workloadPolicy == WorkloadPolicy.NORMAL, Modifier.weight(1f), !dayProcessing) { onSetWorkload(WorkloadPolicy.NORMAL) }
                PolicyChoice("Жёстко", e.workloadPolicy == WorkloadPolicy.INTENSE, Modifier.weight(1f), !dayProcessing) { onSetWorkload(WorkloadPolicy.INTENSE) }
            }
            Text("Бережная нагрузка уменьшает выручку, но сохраняет силы. Интенсивная увеличивает выручку ценой усталости, стресса и дополнительного внимания города.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)

            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            Text("Решения владельца", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            BrothelAction(
                if (e.debt > 0) "Погасить до 5 г. долга" else "Долга нет",
                onRepayDebt,
                Modifier.fillMaxWidth(),
                enabled = !dayProcessing && e.debt > 0 && e.treasury > 0,
            )
            BrothelAction(
                if (e.luxury < 100) "Улучшить комфорт +10 · $luxuryCost г." else "Комфорт максимальный",
                onUpgradeLuxury,
                Modifier.fillMaxWidth(),
                enabled = !dayProcessing && e.luxury < 100 && e.treasury >= luxuryCost,
            )
            Text("Комфорт увеличивает маржу и каждый день немного снижает стресс персонала.", color = Muted, fontSize = 13.sp)
            BrothelAction(
                if (e.secrecy < 100) "Улучшить скрытность +10 · $secrecyCost г." else "Скрытность максимальная",
                onUpgradeSecrecy,
                Modifier.fillMaxWidth(),
                enabled = !dayProcessing && e.secrecy < 100 && e.treasury >= secrecyCost,
            )
            Text("Скрытность уменьшает рост внимания города и при высоком уровне может постепенно его снижать.", color = Muted, fontSize = 13.sp)
            BrothelAction(
                if (e.heat > 0) "Залечь на дно · $layLowCost г." else "Внимания города нет",
                onLayLow,
                Modifier.fillMaxWidth(),
                enabled = !dayProcessing && e.heat > 0 && e.treasury >= layLowCost,
                accent = e.heat >= 60,
            )
            if (e.debt > 0) Text("Раз в три дня кредиторы начисляют проценты. Чем дольше тянуть, тем тяжелее выбраться.", color = Danger, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StaffRequestsScreen(
    state: GameState,
    requests: List<StaffRequest>,
    uiMessage: String?,
    onBack: () -> Unit,
    onResolve: (String, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Просьбы персонала", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DarkCard(Modifier.fillMaxWidth()) {
                Text("Люди помнят ваши решения", color = Ivory, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Принятая просьба укрепляет лояльность. Отказ или игнорирование её ухудшает. Просьбу нужно решить до закрытия указанного дня.", color = Muted, lineHeight = 21.sp)
            }
            uiMessage?.let { MessageStrip(it) }
            if (requests.isEmpty()) DarkCard(Modifier.fillMaxWidth()) {
                Text("Сейчас никто ничего не просит.", color = Ivory)
                Text("После следующих смен ситуация может измениться.", color = Muted)
            }
            requests.forEach { request ->
                DarkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(request.staffName, color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(request.title, color = Bronze, fontWeight = FontWeight.SemiBold)
                        }
                        Text("до дня ${request.expiresDay}", color = Muted, fontSize = 12.sp)
                    }
                    Text(request.body, color = Ivory, lineHeight = 22.sp)
                    Text(requestEffectText(request), color = Muted, fontSize = 13.sp)
                    if (request.cost > 0) Text("Цена решения: ${request.cost} г. · казна ${state.establishment.treasury} г.", color = if (request.cost > state.establishment.treasury) Danger else Bronze, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrothelAction("Отказать", { onResolve(request.id, false) }, Modifier.weight(1f))
                        BrothelAction(requestAcceptLabel(request), { onResolve(request.id, true) }, Modifier.weight(1f), accent = true, enabled = request.cost <= state.establishment.treasury)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun PlanChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PolicyChoice(text, selected, modifier, true, onClick)
}

@Composable
private fun PolicyChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Wine else Raised, contentColor = Ivory, disabledContainerColor = Graphite, disabledContentColor = Muted),
        border = BorderStroke(1.dp, if (selected) Wine else Bronze.copy(alpha = .35f)),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun RecruitmentScreen(
    state: GameState,
    locations: List<RecruitmentLocation>,
    selectedLocation: RecruitmentLocation?,
    candidates: List<RecruitCandidate>,
    candidatePortraits: Map<String, String>,
    candidatePortraitErrors: Map<String, String>,
    generatingCandidateId: String?,
    selectedCandidateId: String?,
    uiMessage: String?,
    onBack: () -> Unit,
    onSelectLocation: (RecruitmentLocation) -> Unit,
    onOpenCandidate: (RecruitCandidate) -> Unit,
    onHire: (RecruitCandidate) -> Unit,
) {
    val selectedCandidate = candidates.firstOrNull { it.id == selectedCandidateId }
    when {
        selectedCandidate != null -> CandidateDetail(
            state,
            selectedCandidate,
            candidatePortraits[selectedCandidate.id],
            candidatePortraitErrors[selectedCandidate.id],
            generatingCandidateId == selectedCandidate.id,
            onBack,
            { onHire(selectedCandidate) },
            uiMessage,
        )
        selectedLocation != null -> CandidateGrid(
            state,
            selectedLocation,
            candidates,
            candidatePortraits,
            candidatePortraitErrors,
            generatingCandidateId,
            onBack,
            onOpenCandidate,
        )
        else -> LocationPicker(locations, onBack, onSelectLocation)
    }
}

@Composable
private fun LocationPicker(locations: List<RecruitmentLocation>, onBack: () -> Unit, onSelectLocation: (RecruitmentLocation) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Город · поиск персонала", onBack)
        Box(
            Modifier.fillMaxWidth().height(300.dp).background(Brush.linearGradient(listOf(Color(0xFF302226), Color(0xFF111416), Color(0xFF30251C)))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Ночной город", color = Ivory, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Три места. Три набора проблем. Денег на ошибку почти нет.", color = Muted)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            locations.forEach { location ->
                DarkCard(Modifier.fillMaxWidth().clickable { onSelectLocation(location) }) {
                    Text(location.name, color = Ivory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(location.district, color = Muted)
                    Text("Риск ${location.risk}/100${if (location.entryCost > 0) " · вход ${location.entryCost} г." else ""}", color = if (location.risk > 65) Danger else Bronze)
                }
            }
        }
    }
}

@Composable
private fun CandidateGrid(
    state: GameState,
    location: RecruitmentLocation,
    candidates: List<RecruitCandidate>,
    candidatePortraits: Map<String, String>,
    candidatePortraitErrors: Map<String, String>,
    generatingCandidateId: String?,
    onBack: () -> Unit,
    onOpenCandidate: (RecruitCandidate) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(location.name, onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Казна: ${state.establishment.treasury} галеонов", color = Bronze)
            Text("Портреты подгружаются по очереди. Карточка показывает первое впечатление и базовые данные.", color = Muted)
            candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    portraitPath = candidatePortraits[candidate.id],
                    errorMessage = candidatePortraitErrors[candidate.id],
                    generating = generatingCandidateId == candidate.id,
                    onOpen = { onOpenCandidate(candidate) },
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: RecruitCandidate,
    portraitPath: String?,
    errorMessage: String?,
    generating: Boolean,
    onOpen: () -> Unit,
) {
    DarkCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(360.dp).background(Brush.verticalGradient(listOf(WineDeep, Color(0xFF202224)))), contentAlignment = Alignment.Center) {
            if (portraitPath != null) LocalFrameImage(portraitPath, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
            else {
                Text(candidate.name.take(1).uppercase(), fontSize = 82.sp, color = Bronze.copy(alpha = .68f))
                if (generating) {
                    PortraitGenerationProgress(
                        candidateName = candidate.name,
                        color = Muted,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                } else {
                    Text(
                        errorMessage ?: "ожидает своей очереди…",
                        color = if (errorMessage != null) Danger else Muted,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.name, color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${candidate.species} · ${candidate.ageYears}", color = Muted)
                }
                Text("${candidate.signingFee} г.", color = Bronze, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Text(candidate.profileHook, color = Ivory, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(candidate.skills.values.sortedByDescending { it.level }.take(2).joinToString(" · ") { "${skillLabel(it.code)} ${it.level}" }, color = Muted)
            Spacer(Modifier.height(10.dp))
            Text("Открыть досье →", color = Bronze)
        }
    }
}

@Composable
private fun CandidateDetail(
    state: GameState,
    candidate: RecruitCandidate,
    portraitPath: String?,
    errorMessage: String?,
    generating: Boolean,
    onBack: () -> Unit,
    onHire: () -> Unit,
    uiMessage: String?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(candidate.name, onBack)
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.linearGradient(listOf(Color(0xFF321E24), Color(0xFF151719)))), contentAlignment = Alignment.Center) {
            if (portraitPath != null) LocalFrameImage(portraitPath, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
            else {
                Text(candidate.name.take(1).uppercase(), fontSize = 110.sp, color = Bronze.copy(alpha = .68f))
                if (generating) {
                    PortraitGenerationProgress(
                        candidateName = candidate.name,
                        color = Muted,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                    )
                } else {
                    Text(
                        errorMessage ?: "портрет ещё в очереди…",
                        color = if (errorMessage != null) Danger else Muted,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${candidate.species} · ${candidate.ageYears}", color = Bronze, fontSize = 16.sp)
            Text(candidate.profileHook, color = Ivory, fontSize = 18.sp, lineHeight = 26.sp)
            HorizontalDivider(color = Bronze.copy(alpha = .28f))
            Text("Характер", color = Muted)
            Text(candidate.traits.take(3).joinToString(" · ").ifBlank { "Пока неясен" }, color = Ivory)
            Text("Навыки", color = Muted)
            candidate.skills.values.sortedByDescending { it.level }.forEach { Text("${skillLabel(it.code)} · ур. ${it.level}", color = Ivory) }
            val likes = candidate.preferences.filterValues { it == PreferenceStance.ENJOY }.keys.map(::serviceLabel)
            val limits = candidate.preferences.filterValues { it == PreferenceStance.HARD_LIMIT }.keys.map(::serviceLabel)
            if (likes.isNotEmpty()) Text("Предпочитает: ${likes.joinToString()}", color = Ivory)
            if (limits.isNotEmpty()) Text("Жёсткие границы: ${limits.joinToString()}", color = Muted)
            uiMessage?.let { MessageStrip(it) }
            BrothelAction(
                if (state.establishment.treasury >= candidate.signingFee) "Нанять за ${candidate.signingFee} г." else "Не хватает денег",
                onHire,
                Modifier.fillMaxWidth(),
                accent = true,
                enabled = state.establishment.treasury >= candidate.signingFee,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StaffListScreen(
    state: GameState,
    galleries: Map<String, List<UiGalleryFrame>>,
    visualProfiles: Map<String, VisualIdentityProfile>,
    onBack: () -> Unit,
    onOpenStaff: (StaffMember) -> Unit,
) {
    val active = state.staff.filter { it.status != StaffStatus.LEFT }
    val leftCount = state.staff.count { it.status == StaffStatus.LEFT }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Персонал", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active.isEmpty()) Text("Здесь пока пусто. Даже сплетничать некому.", color = Muted)
            if (leftCount > 0) Text("Бывший персонал: $leftCount", color = Muted, fontSize = 13.sp)
            active.forEach { member ->
                val frame = canonicalOrLatest(visualProfiles[member.id], galleries[member.id].orEmpty())
                val goal = state.staffGoals.firstOrNull { it.staffId == member.id && it.status == StaffGoalStatus.ACTIVE }
                DarkCard(Modifier.fillMaxWidth().clickable { onOpenStaff(member) }, padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().height(190.dp)) {
                        Box(Modifier.width(140.dp).fillMaxHeight().background(WineDeep), contentAlignment = Alignment.Center) {
                            if (frame != null) LocalFrameImage(frame.absolutePath, Modifier.fillMaxSize(), if (frame.frame.role == GalleryFrameRole.PORTRAIT) ContentScale.Fit else ContentScale.Crop)
                            else Text(member.name.take(1), fontSize = 64.sp, color = Bronze)
                        }
                        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(member.name, color = Ivory, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("${member.species} · ур. ${member.level}", color = Muted)
                            Text(staffMood(member), color = moodColor(member))
                            if (member.loyalty <= 15) Text("Лояльность ${member.loyalty}/100", color = Danger, fontSize = 12.sp)
                            goal?.let { Text("Цель: ${it.title}", color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            Spacer(Modifier.weight(1f))
                            Text("Открыть досье →", color = Bronze)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffDetailScreen(
    state: GameState,
    member: StaffMember,
    diary: String?,
    profile: VisualIdentityProfile?,
    frames: List<UiGalleryFrame>,
    generating: Boolean,
    uiMessage: String?,
    onBack: () -> Unit,
    onGeneratePortrait: () -> Unit,
    onMakeCanonical: (String) -> Unit,
) {
    var tabName by rememberSaveable(member.id) { mutableStateOf(StaffTab.ABOUT.name) }
    val tab = runCatching { StaffTab.valueOf(tabName) }.getOrDefault(StaffTab.ABOUT)
    val hero = canonicalOrLatest(profile, frames)
    val heroScale = if (hero?.frame?.role == GalleryFrameRole.PORTRAIT) ContentScale.Fit else ContentScale.Crop
    val goal = state.staffGoals.firstOrNull { it.staffId == member.id && it.status == StaffGoalStatus.ACTIVE }
    val relations = state.staffRelations.filter { it.involves(member.id) }.sortedWith(compareByDescending<StaffRelation> { it.tension }.thenByDescending { it.affinity })
    val staffById = state.staff.associateBy { it.id }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(member.name, onBack)
        Box(Modifier.fillMaxWidth().height(520.dp).background(WineDeep), contentAlignment = Alignment.Center) {
            if (hero != null) LocalFrameImage(hero.absolutePath, Modifier.fillMaxSize().padding(if (hero.frame.role == GalleryFrameRole.PORTRAIT) 8.dp else 0.dp), heroScale)
            else Text(member.name.take(1), fontSize = 100.sp, color = Bronze)
            Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Coal.copy(alpha = .95f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(member.name, color = Ivory, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${member.species} · уровень ${member.level} · ${staffMood(member)}", color = Muted)
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SmallTab("О ней", tab == StaffTab.ABOUT, Modifier.weight(1f)) { tabName = StaffTab.ABOUT.name }
                SmallTab("Навыки", tab == StaffTab.SKILLS, Modifier.weight(1f)) { tabName = StaffTab.SKILLS.name }
                SmallTab("Дневник", tab == StaffTab.DIARY, Modifier.weight(1f)) { tabName = StaffTab.DIARY.name }
                SmallTab("Галерея", tab == StaffTab.GALLERY, Modifier.weight(1f)) { tabName = StaffTab.GALLERY.name }
            }
            uiMessage?.let { MessageStrip(it) }
            when (tab) {
                StaffTab.ABOUT -> {
                    if (member.loyalty <= 15) Text("Она всерьёз думает об уходе.", color = Danger, fontWeight = FontWeight.Bold)
                    StatLine("Здоровье", member.health)
                    StatLine("Усталость", member.fatigue, inverse = true)
                    StatLine("Стресс", member.stress, inverse = true)
                    StatLine("Лояльность", member.loyalty)
                    Text("План на сегодня: ${planLabel(member)}", color = Bronze)
                    Text("Личные деньги: ${member.personalMoney} галеонов", color = Ivory)
                    goal?.let {
                        HorizontalDivider(color = Bronze.copy(alpha = .22f))
                        Text("Личная цель", color = Muted)
                        Text(it.title, color = Ivory, fontWeight = FontWeight.SemiBold)
                        Text("Прогресс ${it.progress}/${it.target} · срок до дня ${it.deadlineDay}", color = Bronze, fontSize = 13.sp)
                    }
                    if (relations.isNotEmpty()) {
                        HorizontalDivider(color = Bronze.copy(alpha = .22f))
                        Text("Отношения в коллективе", color = Muted)
                        relations.take(5).forEach { relation ->
                            val otherId = relation.other(member.id) ?: return@forEach
                            val other = staffById[otherId] ?: return@forEach
                            Text(
                                "${other.name}: ${relationLabel(relation)} · близость ${relation.affinity} · напряжение ${relation.tension}",
                                color = if (relation.tension >= 45) Danger else Ivory,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (member.inventory.isNotEmpty()) {
                        HorizontalDivider(color = Bronze.copy(alpha = .22f))
                        Text("Личные вещи", color = Muted)
                        Text(member.inventory.joinToString { it.name }, color = Ivory)
                    }
                }
                StaffTab.SKILLS -> {
                    if (member.skills.isEmpty()) Text("Пока нечем хвастаться.", color = Muted)
                    member.skills.values.sortedByDescending { it.level }.forEach { skill ->
                        DarkCard(Modifier.fillMaxWidth()) {
                            Text(skillLabel(skill.code), color = Ivory, fontWeight = FontWeight.Bold)
                            Text("Уровень ${skill.level} · опыт ${skill.xp}", color = Muted)
                        }
                    }
                }
                StaffTab.DIARY -> Text(diary ?: "Сегодня она ничего не записала.", color = Ivory, fontSize = 17.sp, lineHeight = 25.sp)
                StaffTab.GALLERY -> GalleryGrid(frames, profile?.canonicalFrameId, onMakeCanonical)
            }
            if (tab != StaffTab.GALLERY) {
                HorizontalDivider(color = Bronze.copy(alpha = .25f))
                BrothelAction(if (generating) "Создаётся…" else "Новый портрет", onGeneratePortrait, Modifier.fillMaxWidth(), enabled = !generating)
                Text("Кадр дня создаётся автоматически после завершения дня и показывается на главном экране.", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun GalleryGrid(frames: List<UiGalleryFrame>, canonicalFrameId: String?, onMakeCanonical: (String) -> Unit) {
    if (frames.isEmpty()) {
        Text("Галерея пуста. Создайте первый портрет.", color = Muted)
        return
    }
    frames.chunked(2).forEach { rowFrames ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowFrames.forEach { ui ->
                val isCanonical = ui.frame.id == canonicalFrameId
                DarkCard(Modifier.weight(1f), padding = 0.dp) {
                    Box(Modifier.fillMaxWidth().height(220.dp).background(WineDeep)) {
                        LocalFrameImage(ui.absolutePath, Modifier.fillMaxSize(), if (ui.frame.role == GalleryFrameRole.PORTRAIT) ContentScale.Fit else ContentScale.Crop)
                        if (isCanonical) Text("★ эталон", color = Coal, modifier = Modifier.align(Alignment.TopStart).padding(7.dp).background(Bronze, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                    Column(Modifier.padding(8.dp)) {
                        Text("День ${ui.frame.day} · ${if (ui.frame.role == GalleryFrameRole.PORTRAIT) "портрет" else "кадр дня"}", color = Muted, fontSize = 12.sp)
                        if (!isCanonical && ui.frame.role == GalleryFrameRole.PORTRAIT) Text("Сделать эталоном", color = Bronze, fontSize = 13.sp, modifier = Modifier.clickable { onMakeCanonical(ui.frame.id) }.padding(vertical = 5.dp))
                    }
                }
            }
            if (rowFrames.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReportScreen(report: DailyReport?, narrative: String?, onBack: () -> Unit, onNextMorning: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Итоги дня", onBack)
        if (report == null) {
            Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) { Text("День ещё не завершён.", color = Muted) }
        } else {
            Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(WineDeep, Coal))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ДЕНЬ ${report.day}", color = Bronze, fontSize = 18.sp, letterSpacing = 4.sp)
                    Text("ЗАВЕРШЁН", color = Ivory, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Казна ${report.treasuryAfter} · расходы ${report.upkeep}", color = Muted)
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                report.staffReports.forEach { sr ->
                    DarkCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sr.staffName, color = Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("ур. ${sr.levelBefore} → ${sr.levelAfter}", color = Muted)
                            }
                            Text("+${sr.businessRevenue} г.", color = Bronze, fontWeight = FontWeight.Bold)
                        }
                        Text("Клиентов: ${sr.encounters.size} · личная доля ${sr.personalRevenue}", color = Ivory)
                        sr.encounters.take(3).forEach { encounter -> Text("${encounter.client.displayName}: ${serviceLabel(encounter.serviceCode)} · ${outcomeLabel(encounter.outcome)}", color = Muted, fontSize = 13.sp) }
                        sr.incident?.let { note ->
                            val normal = isPlannedDayNote(note)
                            Text((if (normal) "Итог: " else "Происшествие: ") + note, color = if (normal) Muted else Danger)
                        }
                        sr.purchase?.let { Text("Купила: ${it.item.name} · ${it.price} г.", color = Muted) }
                        if (sr.levelAfter > sr.levelBefore) Text("↑ Уровень вырос", color = Bronze)
                    }
                }
                if (!narrative.isNullOrBlank()) {
                    HorizontalDivider(color = Bronze.copy(alpha = .28f))
                    Text("Хроника", color = Bronze, fontWeight = FontWeight.Bold)
                    Text(narrative, color = Ivory, fontSize = 17.sp, lineHeight = 25.sp)
                }
                BrothelAction("На главный экран", onNextMorning, Modifier.fillMaxWidth(), accent = true)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: GameState,
    profiles: Map<String, VisualIdentityProfile>,
    tellamaStatus: String,
    localDreamStatus: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onCheckAi: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onToggleIdentityLock: (String) -> Unit,
    onBack: () -> Unit,
) {
    var devOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    com.sendmefile77.gamebroth.ai.ImageBackendConfig.initialize(context)
    com.sendmefile77.gamebroth.ai.TextBackendConfig.initialize(context)

    var imageModelUri by rememberSaveable { mutableStateOf(com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelUri.orEmpty()) }
    var imageImportStatus by remember { mutableStateOf(com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelImportStatus) }

    var textBackendName by rememberSaveable { mutableStateOf(com.sendmefile77.gamebroth.ai.TextBackendConfig.mode.name) }
    var textModelPath by rememberSaveable { mutableStateOf(com.sendmefile77.gamebroth.ai.TextBackendConfig.modelPath.orEmpty()) }
    var textImportStatus by remember { mutableStateOf(com.sendmefile77.gamebroth.ai.TextBackendConfig.modelImportStatus) }

    val generationProgress by com.sendmefile77.gamebroth.ai.ImageGenerationProgressStore.state.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            imageModelUri = com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelUri.orEmpty()
            imageImportStatus = com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelImportStatus
            textModelPath = com.sendmefile77.gamebroth.ai.TextBackendConfig.modelPath.orEmpty()
            textImportStatus = com.sendmefile77.gamebroth.ai.TextBackendConfig.modelImportStatus
            kotlinx.coroutines.delay(500)
        }
    }

    val imageModelPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            com.sendmefile77.gamebroth.ai.ImageBackendConfig.setModelUri(context, uri.toString())
            imageModelUri = com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelUri.orEmpty()
            imageImportStatus = com.sendmefile77.gamebroth.ai.ImageBackendConfig.modelImportStatus
        }
    }

    val textModelPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            com.sendmefile77.gamebroth.ai.TextBackendConfig.setModelUri(context, uri.toString())
            textModelPath = uri.toString()
            textImportStatus = com.sendmefile77.gamebroth.ai.TextBackendConfig.modelImportStatus
        }
    }

    val selectedTextBackend = runCatching { com.sendmefile77.gamebroth.ai.TextBackendMode.valueOf(textBackendName) }
        .getOrDefault(com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA)

    val visibleTextStatus = if (selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.EMBEDDED && !textImportStatus.startsWith("модель готова")) {
        textImportStatus
    } else tellamaStatus
    val visibleImageStatus = if (!imageImportStatus.startsWith("QNN-комплект готов")) {
        imageImportStatus
    } else localDreamStatus

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Настройки", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Сохранение", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Резервная копия включает игровую базу, историю, просьбы, отношения, цели и все изображения галереи.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrothelAction("Экспорт", onExportBackup, Modifier.weight(1f))
                BrothelAction("Импорт", onImportBackup, Modifier.weight(1f))
            }

            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            Text("Локальные модули", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            StatusLine("Текст", visibleTextStatus)
            StatusLine("Изображения", visibleImageStatus)

            Text("Текстовая модель", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PolicyChoice(
                    "Tellama",
                    selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA,
                    Modifier.weight(1f),
                ) {
                    com.sendmefile77.gamebroth.ai.TextBackendConfig.setMode(context, com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA)
                    textBackendName = com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA.name
                }
                PolicyChoice(
                    "Встроенный",
                    selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.EMBEDDED,
                    Modifier.weight(1f),
                ) {
                    com.sendmefile77.gamebroth.ai.TextBackendConfig.setMode(context, com.sendmefile77.gamebroth.ai.TextBackendMode.EMBEDDED)
                    textBackendName = com.sendmefile77.gamebroth.ai.TextBackendMode.EMBEDDED.name
                }
            }
            Text(
                if (selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA)
                    "Старый вариант сохранён: игра обращается к локальному Ollama/Tellama-серверу на 127.0.0.1:11434."
                else
                    "Встроенный режим использует llama.cpp прямо внутри игры. После хроники GGUF выгружается из памяти перед запуском генератора изображений.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            if (selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.EMBEDDED) {
                BrothelAction(
                    if (textModelPath.isBlank()) "Выбрать модель .gguf" else "Сменить модель .gguf",
                    { textModelPicker.launch(arrayOf("*/*")) },
                    Modifier.fillMaxWidth(),
                )
                Text(
                    when {
                        textModelPath.isBlank() -> "Модель ещё не выбрана."
                        textImportStatus.startsWith("копируем") -> textImportStatus
                        else -> "Модель: ${if (textModelPath.startsWith("content://")) android.net.Uri.parse(textModelPath).lastPathSegment ?: textModelPath else java.io.File(textModelPath).name} · $textImportStatus"
                    },
                    color = if (textImportStatus.startsWith("модель готова")) Bronze else Muted,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val profile = com.sendmefile77.gamebroth.ai.TextBackendConfig.embeddedProfile()
                Text("Профиль телефона: контекст ${profile.contextTokens} · потоки ${profile.threads} · batch ${profile.batchSize} · max output ${profile.maxGeneratedTokens}", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }

            if (selectedTextBackend == com.sendmefile77.gamebroth.ai.TextBackendMode.TELLAMA) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("Ключ текстового сервера (необязательно)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Для локального сервера на 127.0.0.1 ключ обычно не требуется.", color = Muted, fontSize = 13.sp)
                BrothelAction("Сохранить ключ", onSaveApiKey, Modifier.fillMaxWidth())
            }

            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            Text("Генератор изображений", color = Ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Встроенный Local Dream · Snapdragon NPU · QNN 2.48", color = Bronze, fontWeight = FontWeight.Bold)
            Text(
                "Отдельное приложение не требуется. ZIP распаковывается в приватные файлы игры один раз; при следующих запусках используются уже готовые файлы модели.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            BrothelAction(
                if (imageModelUri.isBlank()) "Выбрать SDXL/QNN ZIP" else "Сменить SDXL/QNN ZIP",
                { imageModelPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                Modifier.fillMaxWidth(),
            )
            Text("Выберите lustifyNSFWCheckpoint_zenithV9_qnn2.48_8gen3.zip целиком. Игра проверит комплект и выполнит одноразовую распаковку.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Text(
                imageImportStatus.ifBlank { "QNN-комплект ещё не выбран." },
                color = if (imageImportStatus.startsWith("QNN-комплект готов")) Bronze else Muted,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            if (generationProgress.running || generationProgress.stage == com.sendmefile77.gamebroth.ai.ImageGenerationStage.COMPLETE || generationProgress.stage == com.sendmefile77.gamebroth.ai.ImageGenerationStage.FAILED) {
                DarkCard(Modifier.fillMaxWidth()) {
                    Text("Текущая генерация", color = Ivory, fontWeight = FontWeight.Bold)
                    Text(generationProgress.message, color = if (generationProgress.stage == com.sendmefile77.gamebroth.ai.ImageGenerationStage.FAILED) Danger else Muted, fontSize = 13.sp)
                    val fraction = generationProgress.fraction
                    if (generationProgress.running) {
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = Bronze,
                                trackColor = Raised,
                            )
                            Text(
                                "${generationProgress.counterText.orEmpty()} · ${generationProgress.width}×${generationProgress.height} · seed ${generationProgress.seed ?: "—"}",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Bronze, trackColor = Raised)
                        }
                    }
                }
            }

            BrothelAction("Проверить локальные модули", onCheckAi, Modifier.fillMaxWidth())
            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            Text(if (devOpen) "Скрыть служебный режим" else "Служебный режим", color = Muted, modifier = Modifier.clickable { devOpen = !devOpen }.padding(vertical = 8.dp))
            if (devOpen) DarkCard(Modifier.fillMaxWidth()) {
                Text("Версия UI 2.0 · 1.0.0", color = Bronze)
                Text("День ${state.currentDay} · seed ${state.worldSeed} · schema ${state.schemaVersion}", color = Muted)
                Text("Текстовый модуль: $visibleTextStatus", color = Muted)
                Text("Backend текста: ${com.sendmefile77.gamebroth.ai.TextBackendConfig.label()}", color = Muted)
                Text("Модуль изображений: $visibleImageStatus", color = Muted)
                Text("Backend изображений: ${com.sendmefile77.gamebroth.ai.ImageBackendConfig.label()}", color = Muted)
                (com.sendmefile77.gamebroth.ai.ImageBackendConfig.lastRuntimeError ?: generationProgress.technicalDetail)?.let {
                    Text("Последняя ошибка изображения: $it", color = Danger, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                state.staff.forEach { staff ->
                    profiles[staff.id]?.let { profile ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${staff.name}: visual r${profile.revision}", color = Muted, modifier = Modifier.weight(1f))
                            Text(if (profile.locked) "🔒" else "🔓", modifier = Modifier.clickable { onToggleIdentityLock(staff.id) }.padding(8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusLine(label: String, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(if (status.startsWith("готово")) "готово" else status, color = if (status.startsWith("готово")) Bronze else Muted)
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).background(Graphite).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", color = Ivory, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 5.dp))
        Text(title, color = Ivory, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BrothelAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = if (accent) Wine else Raised
    val border = if (accent) Wine else Bronze.copy(alpha = .42f)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Ivory, disabledContainerColor = Graphite, disabledContentColor = Muted),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SmallTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).background(if (active) Wine else Raised, RoundedCornerShape(3.dp))
            .border(1.dp, if (active) Wine else Bronze.copy(alpha = .28f), RoundedCornerShape(3.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (active) Ivory else Muted, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun HudChip(text: String) {
    Text(text, color = Ivory, fontSize = 12.sp, modifier = Modifier.background(Color.Black.copy(alpha = .58f), RoundedCornerShape(3.dp)).border(1.dp, Bronze.copy(alpha = .35f), RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
}

@Composable
private fun MessageStrip(text: String) {
    Text(text, color = Ivory, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(WineDeep, RoundedCornerShape(4.dp)).border(1.dp, Wine, RoundedCornerShape(4.dp)).padding(10.dp))
}

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.background(Graphite, RoundedCornerShape(5.dp)).border(1.dp, Bronze.copy(alpha = .22f), RoundedCornerShape(5.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun LocalFrameImage(path: String, modifier: Modifier, contentScale: ContentScale) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier.background(WineDeep), contentAlignment = Alignment.Center) { Text("изображение недоступно", color = Muted) }
}

@Composable
private fun StatLine(label: String, value: Int, inverse: Boolean = false) {
    val normalized = value.coerceIn(0, 100) / 100f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, modifier = Modifier.weight(1f))
            Text(value.toString(), color = Ivory)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Raised)) {
            Box(Modifier.fillMaxWidth(normalized).fillMaxHeight().background(if (inverse && value > 70) Danger else Bronze))
        }
    }
}

private fun canonicalOrLatest(profile: VisualIdentityProfile?, frames: List<UiGalleryFrame>): UiGalleryFrame? {
    val canonical = profile?.canonicalFrameId?.let { id -> frames.firstOrNull { it.frame.id == id } }
    return canonical ?: frames.maxByOrNull { it.frame.createdAtEpochMs }
}

private fun requestAcceptLabel(request: StaffRequest): String = when (request.kind) {
    StaffRequestKind.DAY_OFF -> "Дать выходной"
    StaffRequestKind.TRAINING -> "Дать учёбу"
    StaffRequestKind.BONUS -> "Выдать ${request.cost} г."
}

private fun requestEffectText(request: StaffRequest): String = when (request.kind) {
    StaffRequestKind.DAY_OFF -> "Если принять: следующая смена станет отдыхом; лояльность вырастет."
    StaffRequestKind.TRAINING -> "Если принять: следующая смена уйдёт на обучение; материалы оплатятся при закрытии дня."
    StaffRequestKind.BONUS -> "Если принять: деньги сразу перейдут из казны в личные средства сотрудницы."
}

private fun pricingLabel(policy: PricingPolicy): String = when (policy) {
    PricingPolicy.BUDGET -> "доступные"
    PricingPolicy.STANDARD -> "обычные"
    PricingPolicy.PREMIUM -> "премиум"
}

private fun workloadLabel(policy: WorkloadPolicy): String = when (policy) {
    WorkloadPolicy.GENTLE -> "бережная"
    WorkloadPolicy.NORMAL -> "обычная"
    WorkloadPolicy.INTENSE -> "интенсивная"
}

private fun relationLabel(relation: StaffRelation): String = when {
    relation.tension >= 70 -> "открытый конфликт"
    relation.tension >= 45 -> "напряжение"
    relation.affinity >= 65 -> "очень близки"
    relation.affinity >= 35 -> "дружат"
    relation.affinity <= -30 -> "не ладят"
    else -> "нейтрально"
}

private fun planLabel(member: StaffMember): String = when (member.status) {
    StaffStatus.AVAILABLE, StaffStatus.WORKING -> "работа"
    StaffStatus.RESTING -> "отдых"
    StaffStatus.TRAINING -> "учёба"
    StaffStatus.INJURED -> "восстановление"
    StaffStatus.LEFT -> "ушла"
}

private fun planColor(member: StaffMember): Color = when (member.status) {
    StaffStatus.INJURED -> Danger
    StaffStatus.RESTING, StaffStatus.TRAINING -> Bronze
    else -> Muted
}

private fun staffMood(member: StaffMember): String = when {
    member.status == StaffStatus.LEFT -> "ушла"
    member.status == StaffStatus.INJURED -> "травмирована"
    member.status == StaffStatus.TRAINING -> "учится"
    member.status == StaffStatus.RESTING -> "отдыхает"
    member.loyalty <= 15 -> "на грани ухода"
    member.health < 45 -> "плохо себя чувствует"
    member.fatigue > 75 -> "измотана"
    member.stress > 70 -> "на взводе"
    member.loyalty >= 75 -> "довольна"
    member.loyalty < 35 -> "недовольна"
    else -> "в порядке"
}

private fun moodColor(member: StaffMember): Color = when {
    member.status == StaffStatus.LEFT || member.loyalty <= 15 -> Danger
    member.status == StaffStatus.INJURED || member.health < 45 || member.stress > 70 -> Danger
    member.status == StaffStatus.TRAINING || member.status == StaffStatus.RESTING || member.loyalty >= 75 -> Bronze
    else -> Muted
}

private fun isPlannedDayNote(note: String): Boolean {
    val lower = note.lowercase()
    return lower.startsWith("план дня:") || lower.contains("восстановлен") || lower.contains("вынужденный отдых") || lower.contains("обучение отменено")
}

private fun outcomeLabel(outcome: EncounterOutcome): String = when (outcome) {
    EncounterOutcome.EXCELLENT -> "отлично"
    EncounterOutcome.GOOD -> "хорошо"
    EncounterOutcome.ROUTINE -> "обычно"
    EncounterOutcome.AWKWARD -> "неловко"
    EncounterOutcome.REFUSED -> "отказ"
    EncounterOutcome.INCIDENT -> "инцидент"
}

private fun serviceLabel(code: String): String = when (code) {
    "conversation" -> "компания и разговор"
    "massage" -> "массаж"
    "roleplay" -> "ролевая услуга"
    "private_intimacy" -> "приватная близость"
    "arcane_fantasy" -> "магическая фантазия"
    else -> code.replace('_', ' ')
}

private fun skillLabel(code: String): String = when (code) {
    "hospitality" -> "Сервис"
    "social" -> "Общение"
    "bodywork" -> "Телесная работа"
    "roleplay" -> "Роли"
    "intimacy" -> "Интимность"
    "arcane" -> "Магия"
    else -> code.replace('_', ' ')
}
