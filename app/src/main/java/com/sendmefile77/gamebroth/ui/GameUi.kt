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

private enum class Page { HOME, RECRUIT, STAFF, STAFF_DETAIL, REPORT, SETTINGS }
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
    locations: List<RecruitmentLocation>,
    selectedLocation: RecruitmentLocation?,
    candidates: List<RecruitCandidate>,
    candidatePortraits: Map<String, String>,
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
    onCheckAi: () -> Unit,
    onSelectLocation: (RecruitmentLocation) -> Unit,
    onRecruitBack: () -> Unit,
    onHire: (RecruitCandidate) -> Unit,
    onGeneratePortrait: (String) -> Unit,
    onMakeCanonical: (String, String) -> Unit,
    onToggleIdentityLock: (String) -> Unit,
) {
    MaterialTheme(colorScheme = BrothelColors, shapes = BrothelShapes) {
        var pageName by rememberSaveable { mutableStateOf(Page.HOME.name) }
        var selectedStaffId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
        val page = runCatching { Page.valueOf(pageName) }.getOrDefault(Page.HOME)
        val navigate: (Page) -> Unit = { pageName = it.name }

        Surface(Modifier.fillMaxSize(), color = Coal) {
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
                        onRecruit = { selectedCandidateId = null; navigate(Page.RECRUIT) },
                        onStaff = { navigate(Page.STAFF) },
                        onChronicle = { if (report != null) navigate(Page.REPORT) },
                        onCloseDay = onAdvanceDay,
                        onSettings = { navigate(Page.SETTINGS) },
                    )

                    Page.RECRUIT -> RecruitmentScreen(
                        state = state,
                        locations = locations,
                        selectedLocation = selectedLocation,
                        candidates = candidates,
                        candidatePortraits = candidatePortraits,
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
    onRecruit: () -> Unit,
    onStaff: () -> Unit,
    onChronicle: () -> Unit,
    onCloseDay: () -> Unit,
    onSettings: () -> Unit,
) {
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
                BrothelAction("Хроника", onChronicle, Modifier.weight(1f), enabled = reportDay != null)
                BrothelAction(
                    if (dayProcessing) "Закрываем день…" else "Закончить день",
                    onCloseDay,
                    Modifier.weight(1f),
                    accent = true,
                    enabled = !dayProcessing && state.staff.isNotEmpty(),
                )
            }
            uiMessage?.let { MessageStrip(it) }

            if (reportDay != null) {
                Text("Хроника дня $reportDay", color = Bronze, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            val homeText = when {
                narrativeLoading -> "Qwen пишет хронику завершённого дня…"
                !narrative.isNullOrBlank() -> narrative
                reportDay != null -> "Хроника этого дня пока не получена от Qwen. Подробные механические итоги доступны в разделе «Хроника»."
                events.isNotEmpty() -> events.first().summary
                state.staff.isEmpty() -> "Утро пахнет сыростью и дешёвой надеждой. Денег мало, кровать одна, а вывеска звучит куда богаче самого заведения. Пора искать первую сотрудницу."
                else -> "Заведение просыпается. Персонал ждёт распоряжений, город — повода вмешаться."
            }
            Text(homeText, color = Ivory, fontSize = 17.sp, lineHeight = 25.sp)
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
    Box(
        Modifier.fillMaxWidth().height(470.dp).background(
            Brush.verticalGradient(listOf(Color(0xFF261B1B), Coal)),
        ),
    ) {
        if (frame != null) LocalFrameImage(frame.absolutePath, Modifier.fillMaxSize(), ContentScale.Crop)
        else Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF2A2020), Color(0xFF17191A), Color(0xFF26191B))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♜", fontSize = 70.sp, color = Bronze.copy(alpha = .75f))
                Text(title, color = Ivory, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("тёмный город ещё не знает вашего имени", color = Muted, fontSize = 14.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent))),
        )
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
        if (loading) {
            Text(
                "Local Dream создаёт новый кадр дня…",
                color = Ivory,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(130.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Coal.copy(alpha = .94f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, color = Ivory, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RecruitmentScreen(
    state: GameState,
    locations: List<RecruitmentLocation>,
    selectedLocation: RecruitmentLocation?,
    candidates: List<RecruitCandidate>,
    candidatePortraits: Map<String, String>,
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
            generatingCandidateId,
            onBack,
            onOpenCandidate,
        )
        else -> LocationPicker(locations, onBack, onSelectLocation)
    }
}

@Composable
private fun LocationPicker(
    locations: List<RecruitmentLocation>,
    onBack: () -> Unit,
    onSelectLocation: (RecruitmentLocation) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Город · поиск персонала", onBack)
        Box(
            Modifier.fillMaxWidth().height(300.dp).background(
                Brush.linearGradient(listOf(Color(0xFF302226), Color(0xFF111416), Color(0xFF30251C))),
            ),
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
                    Spacer(Modifier.height(6.dp))
                    Text("Риск ${location.risk}/100", color = if (location.risk > 65) Danger else Bronze)
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
    generatingCandidateId: String?,
    onBack: () -> Unit,
    onOpenCandidate: (RecruitCandidate) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(location.name, onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Казна: ${state.establishment.treasury} галеонов", color = Bronze)
            Text("Портреты подгружаются по очереди. Карточка показывает только то, что можно понять до разговора.", color = Muted)
            candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    portraitPath = candidatePortraits[candidate.id],
                    generating = generatingCandidateId == candidate.id,
                ) { onOpenCandidate(candidate) }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CandidateCard(candidate: RecruitCandidate, portraitPath: String?, generating: Boolean, onOpen: () -> Unit) {
    DarkCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), padding = 0.dp) {
        Box(
            Modifier.fillMaxWidth().height(360.dp).background(
                Brush.verticalGradient(listOf(WineDeep, Color(0xFF202224))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (portraitPath != null) {
                LocalFrameImage(portraitPath, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
            } else {
                Text(candidate.name.take(1).uppercase(), fontSize = 82.sp, color = Bronze.copy(alpha = .68f))
                Text(
                    if (generating) "создаём портрет…" else "ожидает своей очереди…",
                    color = Muted,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
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
            val topSkills = candidate.skills.values.sortedByDescending { it.level }.take(2)
            Text(topSkills.joinToString(" · ") { "${skillLabel(it.code)} ${it.level}" }, color = Muted)
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
    generating: Boolean,
    onBack: () -> Unit,
    onHire: () -> Unit,
    uiMessage: String?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(candidate.name, onBack)
        Box(
            Modifier.fillMaxWidth().height(520.dp).background(
                Brush.linearGradient(listOf(Color(0xFF321E24), Color(0xFF151719))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (portraitPath != null) {
                LocalFrameImage(portraitPath, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
            } else {
                Text(candidate.name.take(1).uppercase(), fontSize = 110.sp, color = Bronze.copy(alpha = .68f))
                Text(
                    if (generating) "создаём полный портрет…" else "портрет ещё в очереди…",
                    color = Muted,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${candidate.species} · ${candidate.ageYears}", color = Bronze, fontSize = 16.sp)
            Text(candidate.profileHook, color = Ivory, fontSize = 18.sp, lineHeight = 26.sp)
            HorizontalDivider(color = Bronze.copy(alpha = .28f))
            Text("Характер", color = Muted)
            Text(candidate.traits.take(3).joinToString(" · ").ifBlank { "Пока неясен" }, color = Ivory)
            Text("Навыки", color = Muted)
            candidate.skills.values.sortedByDescending { it.level }.forEach {
                Text("${skillLabel(it.code)} · ур. ${it.level}", color = Ivory)
            }
            val likes = candidate.preferences.filterValues { it == PreferenceStance.ENJOY }.keys.map(::serviceLabel)
            val limits = candidate.preferences.filterValues { it == PreferenceStance.HARD_LIMIT }.keys.map(::serviceLabel)
            if (likes.isNotEmpty()) Text("Предпочитает: ${likes.joinToString()}", color = Ivory)
            if (limits.isNotEmpty()) Text("Жёсткие границы: ${limits.joinToString()}", color = Muted)
            uiMessage?.let { MessageStrip(it) }
            BrothelAction(
                text = if (state.establishment.treasury >= candidate.signingFee) "Нанять за ${candidate.signingFee} г." else "Не хватает денег",
                onClick = onHire,
                modifier = Modifier.fillMaxWidth(),
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Персонал", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.staff.isEmpty()) Text("Здесь пока пусто. Даже сплетничать некому.", color = Muted)
            state.staff.forEach { member ->
                val frame = canonicalOrLatest(visualProfiles[member.id], galleries[member.id].orEmpty())
                DarkCard(Modifier.fillMaxWidth().clickable { onOpenStaff(member) }, padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().height(180.dp)) {
                        Box(Modifier.width(140.dp).fillMaxHeight().background(WineDeep), contentAlignment = Alignment.Center) {
                            if (frame != null) LocalFrameImage(frame.absolutePath, Modifier.fillMaxSize(), ContentScale.Crop)
                            else Text(member.name.take(1), fontSize = 64.sp, color = Bronze)
                        }
                        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(member.name, color = Ivory, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("${member.species} · ур. ${member.level}", color = Muted)
                            Text(staffMood(member), color = moodColor(member))
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar(member.name, onBack)
        Box(Modifier.fillMaxWidth().height(520.dp).background(WineDeep), contentAlignment = Alignment.Center) {
            if (hero != null) LocalFrameImage(hero.absolutePath, Modifier.fillMaxSize().padding(if (hero.frame.role == GalleryFrameRole.PORTRAIT) 8.dp else 0.dp), heroScale)
            else Text(member.name.take(1), fontSize = 100.sp, color = Bronze)
            Box(
                Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Coal.copy(alpha = .95f)))),
            )
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
                    StatLine("Здоровье", member.health)
                    StatLine("Усталость", member.fatigue, inverse = true)
                    StatLine("Стресс", member.stress, inverse = true)
                    StatLine("Лояльность", member.loyalty)
                    Text("Личные деньги: ${member.personalMoney} галеонов", color = Ivory)
                    if (member.inventory.isNotEmpty()) {
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
                BrothelAction(
                    if (generating) "Создаётся…" else "Новый портрет",
                    onGeneratePortrait,
                    Modifier.fillMaxWidth(),
                    enabled = !generating,
                )
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
                        LocalFrameImage(
                            ui.absolutePath,
                            Modifier.fillMaxSize(),
                            if (ui.frame.role == GalleryFrameRole.PORTRAIT) ContentScale.Fit else ContentScale.Crop,
                        )
                        if (isCanonical) Text(
                            "★ эталон", color = Coal,
                            modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                                .background(Bronze, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                    Column(Modifier.padding(8.dp)) {
                        Text("День ${ui.frame.day}", color = Muted, fontSize = 12.sp)
                        if (!isCanonical && ui.frame.role == GalleryFrameRole.PORTRAIT) Text(
                            "Сделать эталоном", color = Bronze, fontSize = 13.sp,
                            modifier = Modifier.clickable { onMakeCanonical(ui.frame.id) }.padding(vertical = 5.dp),
                        )
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
            Box(
                Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(WineDeep, Coal))),
                contentAlignment = Alignment.Center,
            ) {
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
                        Spacer(Modifier.height(8.dp))
                        Text("Клиентов: ${sr.encounters.size} · личная доля ${sr.personalRevenue}", color = Ivory)
                        sr.incident?.let { Text("Происшествие: $it", color = Danger) }
                        sr.purchase?.let { Text("Купила: ${it.item.name} · ${it.price} г.", color = Muted) }
                        if (sr.levelAfter > sr.levelBefore) Text("↑ Уровень вырос", color = Bronze)
                    }
                }
                if (!narrative.isNullOrBlank()) {
                    HorizontalDivider(color = Bronze.copy(alpha = .28f))
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
    onToggleIdentityLock: (String) -> Unit,
    onBack: () -> Unit,
) {
    var devOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTopBar("Настройки", onBack)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Локальные модули", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            StatusLine("Текст", tellamaStatus)
            StatusLine("Изображения", localDreamStatus)
            BrothelAction("Проверить локальные модули", onCheckAi, Modifier.fillMaxWidth())
            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("Ключ текстового сервера (необязательно)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Для локального Qwen на 127.0.0.1 ключ не требуется.", color = Muted, fontSize = 13.sp)
            BrothelAction("Сохранить ключ", onSaveApiKey, Modifier.fillMaxWidth())
            HorizontalDivider(color = Bronze.copy(alpha = .25f))
            Text(
                if (devOpen) "Скрыть служебный режим" else "Служебный режим",
                color = Muted,
                modifier = Modifier.clickable { devOpen = !devOpen }.padding(vertical = 8.dp),
            )
            if (devOpen) DarkCard(Modifier.fillMaxWidth()) {
                Text("Версия UI 1.1 · 0.5.2", color = Bronze)
                Text("День ${state.currentDay} · seed ${state.worldSeed}", color = Muted)
                Text("Текстовый модуль: $tellamaStatus", color = Muted)
                Text("Модуль изображений: $localDreamStatus", color = Muted)
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
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(Graphite).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = Ivory,
            disabledContainerColor = Graphite,
            disabledContentColor = Muted,
        ),
        border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SmallTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).background(if (active) Wine else Raised, RoundedCornerShape(3.dp))
            .border(1.dp, if (active) Wine else Bronze.copy(alpha = .28f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (active) Ivory else Muted, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun HudChip(text: String) {
    Text(
        text, color = Ivory, fontSize = 12.sp,
        modifier = Modifier.background(Color.Black.copy(alpha = .58f), RoundedCornerShape(3.dp))
            .border(1.dp, Bronze.copy(alpha = .35f), RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun MessageStrip(text: String) {
    Text(
        text, color = Ivory, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(WineDeep, RoundedCornerShape(4.dp))
            .border(1.dp, Wine, RoundedCornerShape(4.dp)).padding(10.dp),
    )
}

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.background(Graphite, RoundedCornerShape(5.dp))
            .border(1.dp, Bronze.copy(alpha = .22f), RoundedCornerShape(5.dp))
            .padding(padding),
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

private fun staffMood(member: StaffMember): String = when {
    member.status == StaffStatus.INJURED -> "травмирована"
    member.status == StaffStatus.RESTING -> "отдыхает"
    member.health < 45 -> "плохо себя чувствует"
    member.fatigue > 75 -> "измотана"
    member.stress > 70 -> "на взводе"
    member.loyalty >= 75 -> "довольна"
    member.loyalty < 35 -> "недовольна"
    else -> "в порядке"
}

private fun moodColor(member: StaffMember): Color = when {
    member.status == StaffStatus.INJURED || member.health < 45 || member.stress > 70 -> Danger
    member.loyalty >= 75 -> Bronze
    else -> Muted
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
