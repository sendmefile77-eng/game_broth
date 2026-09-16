package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.DailyReport
import com.sendmefile77.gamebroth.model.EncounterOutcome
import com.sendmefile77.gamebroth.model.GameState
import com.sendmefile77.gamebroth.model.StaffDayReport
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.StaffMemory
import com.sendmefile77.gamebroth.model.StaffRequest
import com.sendmefile77.gamebroth.model.StaffRequestKind
import com.sendmefile77.gamebroth.model.StaffRequestStatus
import com.sendmefile77.gamebroth.model.StaffStatus
import com.sendmefile77.gamebroth.model.WorldEvent
import kotlin.math.absoluteValue

data class StaffLifeResult(
    val state: GameState,
    val requestUpdates: List<StaffRequest>,
    val events: List<WorldEvent>,
    val memories: List<StaffMemory>,
)

data class StaffRequestResolution(
    val state: GameState,
    val request: StaffRequest,
    val event: WorldEvent,
    val memory: StaffMemory,
)

class StaffLifeEngine {
    fun afterDay(
        state: GameState,
        report: DailyReport,
        knownRequests: List<StaffRequest>,
    ): StaffLifeResult {
        require(state.currentDay == report.day + 1)
        val events = mutableListOf<WorldEvent>()
        val memories = mutableListOf<StaffMemory>()
        val requestUpdates = mutableListOf<StaffRequest>()
        var workingState = state.copy(schemaVersion = maxOf(state.schemaVersion, 5))

        knownRequests
            .filter { it.status == StaffRequestStatus.PENDING && it.expiresDay < workingState.currentDay }
            .forEachIndexed { index, request ->
                val expired = request.copy(status = StaffRequestStatus.EXPIRED, resolvedDay = report.day)
                workingState = applyRefusalEffects(workingState, expired)
                requestUpdates += expired
                events += event(
                    report.day,
                    40 + index,
                    "STAFF_REQUEST_EXPIRED",
                    "${request.staffName}: просьба «${request.title}» осталась без ответа.",
                    "requestId=${request.id};staffId=${request.staffId};kind=${request.kind.name}",
                )
                memories += memory(
                    request.staffId,
                    report.day,
                    40 + index,
                    "request",
                    "Моя просьба «${request.title}» осталась без ответа.",
                    3,
                )
            }

        val reportByStaff = report.staffReports.associateBy { it.staffId }
        workingState = workingState.copy(
            staff = workingState.staff.mapIndexed { index, member ->
                if (member.status == StaffStatus.LEFT) return@mapIndexed member
                val staffReport = reportByStaff[member.id]
                val delta = dailyLoyaltyDelta(member, staffReport)
                if (delta != 0) {
                    memories += memory(
                        member.id,
                        report.day,
                        100 + index,
                        "loyalty",
                        loyaltyMemory(delta, staffReport),
                        if (delta.absoluteValue >= 3) 3 else 2,
                    )
                }
                member.copy(loyalty = (member.loyalty + delta).coerceIn(0, 100))
            },
        )

        val leavingIds = workingState.staff
            .filter { it.status != StaffStatus.LEFT && it.loyalty <= LEAVE_THRESHOLD }
            .map { it.id }
            .toSet()
        if (leavingIds.isNotEmpty()) {
            workingState = workingState.copy(
                staff = workingState.staff.mapIndexed { index, member ->
                    if (member.id !in leavingIds) member
                    else {
                        events += event(
                            report.day,
                            180 + index,
                            "STAFF_LEFT",
                            "${member.name} ушла из заведения: лояльность упала до ${member.loyalty}.",
                            "staffId=${member.id};loyalty=${member.loyalty}",
                        )
                        memories += memory(
                            member.id,
                            report.day,
                            180 + index,
                            "departure",
                            "Я решила уйти. Дальше оставаться здесь уже не хочу.",
                            5,
                        )
                        member.copy(status = StaffStatus.LEFT)
                    }
                },
            )
            knownRequests
                .filter { it.status == StaffRequestStatus.PENDING && it.staffId in leavingIds }
                .forEach { request ->
                    if (requestUpdates.none { it.id == request.id }) {
                        requestUpdates += request.copy(status = StaffRequestStatus.EXPIRED, resolvedDay = report.day)
                    }
                }
        }

        val stillPendingStaff = (knownRequests + requestUpdates)
            .groupBy { it.id }
            .mapValues { (_, versions) -> versions.last() }
            .values
            .filter { it.status == StaffRequestStatus.PENDING }
            .map { it.staffId }
            .toSet()

        val candidates = workingState.staff
            .filter { it.status != StaffStatus.LEFT && it.status != StaffStatus.INJURED && it.id !in stillPendingStaff }
            .mapNotNull { member -> buildRequest(workingState, report, member) }
            .sortedWith(compareByDescending<StaffRequest> { urgency(it) }.thenBy { it.staffId })
            .take(MAX_NEW_REQUESTS_PER_DAY)

        candidates.forEachIndexed { index, request ->
            requestUpdates += request
            events += event(
                report.day,
                240 + index,
                "STAFF_REQUESTED",
                "${request.staffName}: ${request.title}.",
                "requestId=${request.id};staffId=${request.staffId};kind=${request.kind.name};expiresDay=${request.expiresDay}",
            )
            memories += memory(
                request.staffId,
                report.day,
                240 + index,
                "request",
                "Я попросила: ${request.body}",
                2,
            )
        }

        val social = StaffSocialEngine().afterDay(workingState, report)
        return StaffLifeResult(
            state = social.state,
            requestUpdates = requestUpdates,
            events = events + social.events,
            memories = memories + social.memories,
        )
    }

    fun resolve(state: GameState, request: StaffRequest, accept: Boolean): StaffRequestResolution {
        require(request.status == StaffRequestStatus.PENDING) { "Request already resolved" }
        val member = state.staff.firstOrNull { it.id == request.staffId }
            ?: error("Staff member not found")
        require(member.status != StaffStatus.LEFT) { "Staff member already left" }
        if (accept && request.cost > state.establishment.treasury) error("Not enough treasury")

        val resolved = request.copy(
            status = if (accept) StaffRequestStatus.ACCEPTED else StaffRequestStatus.REFUSED,
            resolvedDay = state.currentDay,
        )
        val nextState = if (accept) applyAcceptEffects(state, resolved) else applyRefusalEffects(state, resolved)
        val action = if (accept) "просьба принята" else "просьба отклонена"
        val outcomeCode = if (accept) "accepted" else "refused"
        val order = 600 + stableOrder(request.id)
        val event = WorldEvent(
            id = "request-event-${request.id}-$outcomeCode",
            day = state.currentDay,
            type = if (accept) "STAFF_REQUEST_ACCEPTED" else "STAFF_REQUEST_REFUSED",
            summary = "${request.staffName}: $action — «${request.title}».",
            payload = "requestId=${request.id};staffId=${request.staffId};kind=${request.kind.name};accepted=$accept;cost=${request.cost}",
            createdAtEpochMs = timestamp(state.currentDay, order),
        )
        val memory = StaffMemory(
            id = "request-memory-${request.id}-$outcomeCode",
            staffId = request.staffId,
            day = state.currentDay,
            category = "request_result",
            summary = if (accept) "Мою просьбу «${request.title}» приняли." else "Мне отказали в просьбе «${request.title}».",
            importance = if (accept) 3 else 4,
            createdAtEpochMs = timestamp(state.currentDay, order),
        )
        return StaffRequestResolution(nextState, resolved, event, memory)
    }

    private fun applyAcceptEffects(state: GameState, request: StaffRequest): GameState {
        val nextTreasury = state.establishment.treasury - request.cost
        return state.copy(
            establishment = state.establishment.copy(treasury = nextTreasury),
            staff = state.staff.map { member ->
                if (member.id != request.staffId) member
                else {
                    val plannedStatus = when (request.kind) {
                        StaffRequestKind.DAY_OFF -> if (member.status == StaffStatus.INJURED) StaffStatus.INJURED else StaffStatus.RESTING
                        StaffRequestKind.TRAINING -> if (member.status == StaffStatus.INJURED) StaffStatus.INJURED else StaffStatus.TRAINING
                        StaffRequestKind.BONUS -> member.status
                    }
                    member.copy(
                        personalMoney = member.personalMoney + if (request.kind == StaffRequestKind.BONUS) request.cost else 0,
                        loyalty = (member.loyalty + request.loyaltyOnAccept).coerceIn(0, 100),
                        stress = (member.stress + request.stressOnAccept).coerceIn(0, 100),
                        status = plannedStatus,
                    )
                }
            },
        )
    }

    private fun applyRefusalEffects(state: GameState, request: StaffRequest): GameState = state.copy(
        staff = state.staff.map { member ->
            if (member.id != request.staffId || member.status == StaffStatus.LEFT) member
            else member.copy(
                loyalty = (member.loyalty + request.loyaltyOnRefuse).coerceIn(0, 100),
                stress = (member.stress + request.stressOnRefuse).coerceIn(0, 100),
            )
        },
    )

    private fun dailyLoyaltyDelta(member: StaffMember, report: StaffDayReport?): Int {
        if (report == null) return 0
        val note = report.incident.orEmpty()
        var delta = when {
            note.startsWith("План дня: отдых") -> 2
            note.startsWith("План дня: обучение") -> 2
            note.startsWith("Восстановление после травмы") -> 0
            note.startsWith("Обучение отменено") -> -1
            report.encounters.isEmpty() -> 0
            else -> 0
        }
        if (report.encounters.any { it.outcome == EncounterOutcome.EXCELLENT }) delta += 1
        if (report.encounters.any { it.outcome == EncounterOutcome.INCIDENT }) delta -= 2
        if (report.encounters.any { it.outcome == EncounterOutcome.REFUSED }) delta += 1
        if (report.encounters.isNotEmpty() && report.personalRevenue == 0L) delta -= 1
        if (member.fatigue >= 85) delta -= 2
        else if (member.fatigue >= 75) delta -= 1
        if (member.stress >= 80) delta -= 2
        else if (member.stress >= 70) delta -= 1
        if (member.health < 45) delta -= 1
        return delta.coerceIn(-5, 4)
    }

    private fun loyaltyMemory(delta: Int, report: StaffDayReport?): String = when {
        delta >= 3 -> "Сегодня отношение к заведению заметно улучшилось."
        delta > 0 -> "Сегодня у меня стало чуть больше причин доверять этому месту."
        delta <= -3 -> "После сегодняшнего дня я всерьёз злюсь на то, как здесь идут дела."
        delta < 0 -> "Сегодня моё отношение к заведению стало хуже."
        else -> report?.incident ?: "День не изменил моего отношения к заведению."
    }

    private fun buildRequest(state: GameState, report: DailyReport, member: StaffMember): StaffRequest? {
        val kind = when {
            member.fatigue >= 72 || member.stress >= 68 || member.health < 55 -> StaffRequestKind.DAY_OFF
            member.loyalty <= 32 -> StaffRequestKind.BONUS
            weakestSkillLevel(member) <= 2 && deterministicRoll(state, report.day, member, 3) == 0 -> StaffRequestKind.TRAINING
            deterministicRoll(state, report.day, member, 5) == 0 -> if (member.fatigue >= 45) StaffRequestKind.DAY_OFF else StaffRequestKind.TRAINING
            else -> return null
        }
        val cost = if (kind == StaffRequestKind.BONUS) (3L + member.level / 3L).coerceAtMost(8L) else 0L
        val title: String
        val body: String
        val accept: Int
        val refuse: Int
        val stressAccept: Int
        val stressRefuse: Int
        when (kind) {
            StaffRequestKind.DAY_OFF -> {
                title = "Просит выходной"
                body = when {
                    member.fatigue >= 72 -> "${member.name} говорит, что вымоталась и просит снять её со следующей смены."
                    member.stress >= 68 -> "${member.name} просит день тишины без клиентов: нервы уже на пределе."
                    else -> "${member.name} просит день на восстановление, пока состояние не стало хуже."
                }
                accept = 5; refuse = -4; stressAccept = -4; stressRefuse = 4
            }
            StaffRequestKind.TRAINING -> {
                title = "Хочет учиться"
                body = "${member.name} просит следующую смену отдать под обучение. Материалы будут списаны как обычная стоимость тренировки."
                accept = 4; refuse = -2; stressAccept = -2; stressRefuse = 1
            }
            StaffRequestKind.BONUS -> {
                title = "Просит личный бонус"
                body = "${member.name} считает, что ей нужен знак уважения от заведения и просит $cost галеонов лично себе."
                accept = 7; refuse = -5; stressAccept = -2; stressRefuse = 3
            }
        }
        return StaffRequest(
            id = "request-${report.day}-${member.id}-${kind.name.lowercase()}",
            staffId = member.id,
            staffName = member.name,
            createdDay = report.day,
            expiresDay = state.currentDay,
            kind = kind,
            title = title,
            body = body,
            cost = cost,
            loyaltyOnAccept = accept,
            loyaltyOnRefuse = refuse,
            stressOnAccept = stressAccept,
            stressOnRefuse = stressRefuse,
        )
    }

    private fun weakestSkillLevel(member: StaffMember): Int = member.skills.values.minOfOrNull { it.level } ?: 1

    private fun deterministicRoll(state: GameState, day: Int, member: StaffMember, modulus: Int): Int {
        val value = state.worldSeed xor member.id.hashCode().toLong() xor (day.toLong() * GOLDEN_GAMMA)
        val folded = (value xor (value ushr 32)).toInt()
        return (folded and Int.MAX_VALUE) % modulus
    }

    private fun stableOrder(value: String): Int = value.hashCode().and(Int.MAX_VALUE) % 200

    private fun urgency(request: StaffRequest): Int = when (request.kind) {
        StaffRequestKind.DAY_OFF -> 30
        StaffRequestKind.BONUS -> 20
        StaffRequestKind.TRAINING -> 10
    }

    private fun event(day: Int, order: Int, type: String, summary: String, payload: String = "") = WorldEvent(
        id = "life-event-$day-$order-$type",
        day = day,
        type = type,
        summary = summary,
        payload = payload,
        createdAtEpochMs = timestamp(day, order),
    )

    private fun memory(staffId: String, day: Int, order: Int, category: String, summary: String, importance: Int) = StaffMemory(
        id = "life-memory-$day-$order-$staffId-$category",
        staffId = staffId,
        day = day,
        category = category,
        summary = summary,
        importance = importance,
        createdAtEpochMs = timestamp(day, order),
    )

    private fun timestamp(day: Int, order: Int): Long = SIM_EPOCH + day * DAY_MS + 20_000 + order

    companion object {
        private const val LEAVE_THRESHOLD = 8
        private const val MAX_NEW_REQUESTS_PER_DAY = 2
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val SIM_EPOCH = 946684800000L
        private const val DAY_MS = 86_400_000L
    }
}
