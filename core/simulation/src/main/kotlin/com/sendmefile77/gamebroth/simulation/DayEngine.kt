package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import kotlin.math.max
import kotlin.random.Random

data class LedgerEntry(val code: String, val amount: Long, val description: String)
data class DayResult(
    val state: GameState,
    val ledger: List<LedgerEntry>,
    val events: List<WorldEvent>,
    val memories: List<StaffMemory>,
    val report: DailyReport,
)

class DayEngine {
    fun advanceDay(state: GameState): DayResult {
        val day = state.currentDay
        val random = Random(state.worldSeed xor (day.toLong() * GOLDEN_GAMMA))
        val records = RecordFactory(day)
        val ledger = mutableListOf<LedgerEntry>()
        val events = mutableListOf<WorldEvent>()
        val memories = mutableListOf<StaffMemory>()
        val reports = mutableListOf<StaffDayReport>()

        val updatedStaff = state.staff.map { member ->
            when (member.status) {
                StaffStatus.AVAILABLE, StaffStatus.WORKING -> {
                    val result = simulateWorkingDay(member, day, random, records, ledger, events, memories)
                    reports += result.report
                    result.member
                }
                StaffStatus.RESTING -> {
                    val result = simulateRestDay(member, day, records, memories)
                    reports += result.report
                    result.member
                }
                StaffStatus.TRAINING -> {
                    val result = simulateTrainingDay(member, day, records, ledger, events, memories)
                    reports += result.report
                    result.member
                }
                StaffStatus.INJURED -> {
                    val result = simulateRecoveryDay(member, day, records, memories)
                    reports += result.report
                    result.member
                }
                StaffStatus.LEFT -> member
            }
        }

        val revenue = reports.sumOf { it.businessRevenue }
        val baseUpkeep = 3L + updatedStaff.count { it.status != StaffStatus.LEFT } * 2L
        val trainingCosts = ledger.filter { it.code.startsWith("TRAINING:") && it.amount < 0 }.sumOf { -it.amount }
        val upkeep = baseUpkeep + trainingCosts
        ledger += LedgerEntry("UPKEEP", -baseUpkeep, "Аренда, еда и базовые расходы")
        val cashAfter = state.establishment.treasury + revenue - upkeep
        val treasuryAfter = cashAfter.coerceAtLeast(0)
        val debtDelta = (-cashAfter).coerceAtLeast(0)
        if (debtDelta > 0) events += records.event("DEBT_INCREASED", "Казны не хватило; долг вырос на $debtDelta.")

        val repDelta = when {
            revenue >= 40 -> 2
            revenue >= 15 -> 1
            updatedStaff.none { it.status != StaffStatus.LEFT } -> -1
            else -> 0
        }
        val heatDelta = reports.sumOf { report -> report.encounters.count { it.outcome == EncounterOutcome.INCIDENT } }
        val nextState = state.copy(
            schemaVersion = max(state.schemaVersion, 6),
            currentDay = day + 1,
            establishment = state.establishment.copy(
                treasury = treasuryAfter,
                debt = state.establishment.debt + debtDelta,
                publicReputation = (state.establishment.publicReputation + repDelta).coerceIn(-100, 100),
                heat = (state.establishment.heat + heatDelta).coerceIn(0, 100),
            ),
            staff = updatedStaff,
        )
        events += records.event(
            "DAY_CLOSED",
            "День $day завершён. Выручка: $revenue, расходы: $upkeep, казна: $treasuryAfter.",
            "businessRevenue=$revenue;upkeep=$upkeep;trainingCosts=$trainingCosts;treasury=$treasuryAfter;debtDelta=$debtDelta",
        )
        val report = DailyReport(day, reports, revenue, upkeep, treasuryAfter, debtDelta, records.timestamp(900))
        val core = DayResult(nextState, ledger, events, memories, report)
        return EstablishmentEngine().applyDay(state, core)
    }

    private fun simulateRestDay(
        member: StaffMember,
        day: Int,
        records: RecordFactory,
        memories: MutableList<StaffMemory>,
    ): PlannedDayOutcome {
        val after = member.copy(
            fatigue = (member.fatigue - 24).coerceAtLeast(0),
            stress = (member.stress - 14).coerceAtLeast(0),
            health = (member.health + 2).coerceAtMost(100),
            loyalty = (member.loyalty + 1).coerceAtMost(100),
            status = StaffStatus.AVAILABLE,
        )
        val note = "План дня: отдых. Смена пропущена сознательно ради восстановления."
        memories += records.memory(member.id, "rest", "Получила день отдыха и смогла восстановиться.", 2)
        memories += records.memory(
            member.id,
            "diary",
            "Сегодня меня оставили без смены. К вечеру усталость снизилась до ${after.fatigue}/100, стресс — до ${after.stress}/100, здоровье ${after.health}/100. Иногда лучший заработок — не довести себя до состояния, когда работать уже невозможно.",
            2,
        )
        return PlannedDayOutcome(after, StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = note))
    }

    private fun simulateRecoveryDay(
        member: StaffMember,
        day: Int,
        records: RecordFactory,
        memories: MutableList<StaffMemory>,
    ): PlannedDayOutcome {
        val nextHealth = (member.health + 6).coerceAtMost(100)
        val after = member.copy(
            health = nextHealth,
            fatigue = (member.fatigue - 16).coerceAtLeast(0),
            stress = (member.stress - 6).coerceAtLeast(0),
            status = if (nextHealth >= 55) StaffStatus.AVAILABLE else StaffStatus.INJURED,
        )
        val note = "Восстановление после травмы. Рабочая смена недоступна."
        memories += records.memory(member.id, "health", "День ушёл на восстановление после травмы.", 3)
        memories += records.memory(
            member.id,
            "diary",
            "Сегодня пришлось лечиться и отлеживаться. Здоровье к вечеру ${after.health}/100, усталость ${after.fatigue}/100, стресс ${after.stress}/100. До обычной смены ещё нужно дожить, а не только доползти.",
            3,
        )
        return PlannedDayOutcome(after, StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = note))
    }

    private fun simulateTrainingDay(
        member: StaffMember,
        day: Int,
        records: RecordFactory,
        ledger: MutableList<LedgerEntry>,
        events: MutableList<WorldEvent>,
        memories: MutableList<StaffMemory>,
    ): PlannedDayOutcome {
        if (member.health < 40 || member.fatigue > 88) {
            val forced = member.copy(
                fatigue = (member.fatigue - 18).coerceAtLeast(0),
                stress = (member.stress - 10).coerceAtLeast(0),
                health = (member.health + 2).coerceAtMost(100),
                status = if (member.health + 2 < 45) StaffStatus.INJURED else StaffStatus.AVAILABLE,
            )
            memories += records.memory(member.id, "health", "Обучение пришлось отменить из-за состояния.", 2)
            return PlannedDayOutcome(forced, StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = "Обучение отменено: состояние потребовало отдыха."))
        }

        val target = trainingTarget(member)
        val beforeSkill = member.skills[target] ?: SkillProgress(target)
        val gain = 18 + member.level * 2
        val afterSkill = levelSkill(beforeSkill, gain)
        val cost = 2L
        ledger += LedgerEntry("TRAINING:${member.id}", -cost, "Обучение ${member.name}: ${skillLabel(target)}")
        if (afterSkill.level > beforeSkill.level) events += records.event("SKILL_LEVEL_UP", "${member.name}: навык «${skillLabel(target)}» вырос до ${afterSkill.level}.")
        val after = member.copy(
            skills = member.skills + (target to afterSkill),
            fatigue = (member.fatigue + 6).coerceAtMost(100),
            stress = (member.stress - 5).coerceAtLeast(0),
            loyalty = (member.loyalty + 1).coerceAtMost(100),
            status = StaffStatus.AVAILABLE,
        )
        val note = "План дня: обучение · ${skillLabel(target)} ${beforeSkill.level}→${afterSkill.level}, +$gain опыта."
        memories += records.memory(member.id, "training", "Тренировала ${skillLabel(target)}: +$gain опыта.", 2)
        memories += records.memory(
            member.id,
            "diary",
            "Сегодня вместо клиентов было обучение: ${skillLabel(target)}. Получила $gain опыта; уровень навыка теперь ${afterSkill.level}. Усталость к вечеру ${after.fatigue}/100, стресс ${after.stress}/100. Не самый прибыльный день, зато завтра ошибки будут стоить чуть дешевле.",
            2,
        )
        return PlannedDayOutcome(after, StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = note))
    }

    private fun trainingTarget(member: StaffMember): String = TRAINING_SKILLS.minWithOrNull(
        compareBy<String> { member.skills[it]?.level ?: 1 }.thenBy { member.skills[it]?.xp ?: 0 }.thenBy { it },
    ) ?: "hospitality"

    private fun simulateWorkingDay(
        member: StaffMember,
        day: Int,
        random: Random,
        records: RecordFactory,
        ledger: MutableList<LedgerEntry>,
        events: MutableList<WorldEvent>,
        memories: MutableList<StaffMemory>,
    ): WorkingDayOutcome {
        if (member.health < 35 || member.fatigue > 90) {
            val forced = member.copy(
                fatigue = (member.fatigue - 18).coerceAtLeast(0),
                stress = (member.stress - 10).coerceAtLeast(0),
                health = (member.health + 2).coerceAtMost(100),
                status = if (member.health + 2 < 45) StaffStatus.INJURED else StaffStatus.AVAILABLE,
            )
            events += records.event("STAFF_FORCED_REST", "${member.name} пропустила смену из-за состояния.")
            memories += records.memory(member.id, "health", "Пришлось пропустить смену из-за плохого состояния.", 2)
            memories += records.memory(
                member.id,
                "diary",
                "Сегодня организм сам отменил рабочие планы. К вечеру усталость ${forced.fatigue}/100, стресс ${forced.stress}/100, здоровье ${forced.health}/100. Иногда приказ «работать» не сильнее обычной физиологии.",
                3,
            )
            return WorkingDayOutcome(forced, StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = "Вынужденный отдых из-за состояния."))
        }

        val count = when {
            member.fatigue >= 75 -> 1
            member.level >= 8 -> 3
            member.level >= 3 -> 2
            else -> 1 + random.nextInt(0, 2)
        }
        var working = member.copy(status = StaffStatus.WORKING)
        val encounters = mutableListOf<WorkEncounter>()
        var business = 0L
        var personal = 0L
        var incident: String? = null

        repeat(count) { index ->
            val client = generateClient(member, day, index, random)
            val encounter = simulateEncounter(working, client, day, index, random)
            encounters += encounter
            business += encounter.businessCut
            personal += encounter.staffCut
            working = applyEncounter(working, encounter)
            encounter.incident?.let { incident = it }
            ledger += LedgerEntry("SESSION:${encounter.id}", encounter.businessCut, "${member.name}: ${client.displayName}")
            memories += records.memory(member.id, "work", "${client.displayName}: ${encounter.summary}", if (encounter.incident == null) 1 else 3)
        }

        val levelPair = levelCharacter(working.level, working.xp + 7 + encounters.size * 5)
        if (levelPair.first > member.level) {
            events += records.event("STAFF_LEVEL_UP", "${member.name} достигла уровня ${levelPair.first}.")
            memories += records.memory(member.id, "progress", "Получен уровень ${levelPair.first}.", 3)
        }
        working = working.copy(
            level = levelPair.first,
            xp = levelPair.second,
            personalMoney = working.personalMoney + personal,
            status = if (working.health < 45) StaffStatus.INJURED else StaffStatus.AVAILABLE,
        )
        val purchase = maybeBuy(working, day, random)
        working = purchase.member
        purchase.purchase?.let {
            memories += records.memory(member.id, "purchase", "Купила ${it.item.name} за ${it.price}: ${it.reason}", 2)
            events += records.event("STAFF_PURCHASE", "${member.name} купила ${it.item.name} на личные деньги.", "staffId=${member.id};price=${it.price}")
        }
        memories += records.memory(member.id, "diary", diary(member, encounters, purchase.purchase, working), if (incident == null) 2 else 4)
        return WorkingDayOutcome(working, StaffDayReport(day, member.id, member.name, member.level, working.level, encounters, business, personal, purchase.purchase, incident))
    }

    private fun generateClient(member: StaffMember, day: Int, index: Int, random: Random): ClientProfile {
        val a = CLIENTS.random(random)
        return ClientProfile(
            id = "client-$day-${member.id}-$index-${random.nextInt(1000,9999)}",
            displayName = a.names.random(random),
            archetype = a.title,
            ageYears = a.age.random(random),
            wealth = (a.wealth + random.nextInt(-10,16)).coerceIn(1,100),
            patience = random.nextInt(25,91),
            discretion = random.nextInt(20,96),
            interests = SERVICES.shuffled(random).take(if (random.nextBoolean()) 1 else 2).toSet(),
        )
    }

    private fun simulateEncounter(member: StaffMember, client: ClientProfile, day: Int, index: Int, random: Random): WorkEncounter {
        val service = client.interests.random(random)
        val stance = member.preferences[service] ?: PreferenceStance.ACCEPT
        val skillCode = skillFor(service)
        if (stance == PreferenceStance.HARD_LIMIT) {
            return WorkEncounter(
                "enc-$day-${member.id}-$index", day, member.id, client, service, EncounterOutcome.REFUSED,
                "Запрос пересёк личную границу; сотрудница отказала и предложила другой формат.",
                0, 0, 0, skillCode, 1, 0, if (client.patience < 40) 2 else 0, 0,
                if (client.patience < 30) "Клиент спорил после отказа, но граница была соблюдена." else null,
            )
        }
        val skill = member.skills[skillCode] ?: SkillProgress(skillCode)
        val pref = when (stance) {
            PreferenceStance.ENJOY -> 8
            PreferenceStance.ACCEPT -> 2
            PreferenceStance.AVOID -> -8
            PreferenceStance.HARD_LIMIT -> -100
        }
        val conditionPenalty = member.fatigue / 8 + member.stress / 10 + (100 - member.health) / 12
        val score = 35 + skill.level * 6 + member.level * 2 + pref - conditionPenalty + random.nextInt(-18,19)
        val outcome = when {
            score >= 72 -> EncounterOutcome.EXCELLENT
            score >= 57 -> EncounterOutcome.GOOD
            score >= 38 -> EncounterOutcome.ROUTINE
            score >= 24 -> EncounterOutcome.AWKWARD
            else -> EncounterOutcome.INCIDENT
        }
        val multiplier = when (outcome) {
            EncounterOutcome.EXCELLENT -> 1.45
            EncounterOutcome.GOOD -> 1.20
            EncounterOutcome.ROUTINE -> 1.0
            EncounterOutcome.AWKWARD -> .75
            EncounterOutcome.INCIDENT -> .35
            EncounterOutcome.REFUSED -> 0.0
        }
        val gross = max(1L, (PRICES.getValue(service) * multiplier * (0.75 + client.wealth / 120.0)).toLong())
        val staffCut = max(1L, gross / 4)
        val healthDelta = if (outcome == EncounterOutcome.INCIDENT && random.nextInt(100) < 35) -random.nextInt(1,5) else 0
        val incident = when {
            outcome == EncounterOutcome.INCIDENT && healthDelta < 0 -> "Небольшое повреждение; нужна передышка."
            outcome == EncounterOutcome.INCIDENT -> "Конфликтный клиент, встречу пришлось закончить раньше."
            outcome == EncounterOutcome.AWKWARD -> "Неловкая встреча без серьёзных последствий."
            else -> null
        }
        val summary = when (outcome) {
            EncounterOutcome.EXCELLENT -> "Очень удачная встреча, клиент щедро заплатил."
            EncounterOutcome.GOOD -> "Хорошая встреча, клиент остался доволен."
            EncounterOutcome.ROUTINE -> "Обычная рабочая встреча без проблем."
            EncounterOutcome.AWKWARD -> "Запрос оказался неудобным, но личные границы не нарушались."
            EncounterOutcome.INCIDENT -> "Встречу пришлось прервать."
            EncounterOutcome.REFUSED -> "Запрос отклонён."
        }
        return WorkEncounter(
            "enc-$day-${member.id}-$index", day, member.id, client, service, outcome, summary,
            gross, staffCut, gross - staffCut, skillCode,
            when (outcome) {
                EncounterOutcome.EXCELLENT -> 14
                EncounterOutcome.GOOD -> 10
                EncounterOutcome.ROUTINE -> 7
                EncounterOutcome.AWKWARD -> 5
                EncounterOutcome.INCIDENT -> 3
                EncounterOutcome.REFUSED -> 1
            },
            when (service) {
                "conversation" -> 3
                "massage" -> 5
                "roleplay" -> 6
                "private_intimacy" -> 7
                "arcane_fantasy" -> 8
                else -> 5
            },
            when (outcome) {
                EncounterOutcome.EXCELLENT -> -1
                EncounterOutcome.GOOD -> 0
                EncounterOutcome.ROUTINE -> 1
                EncounterOutcome.AWKWARD -> 4
                EncounterOutcome.INCIDENT -> 8
                EncounterOutcome.REFUSED -> 0
            } + if (stance == PreferenceStance.AVOID) 3 else 0,
            healthDelta,
            incident,
        )
    }

    private fun applyEncounter(member: StaffMember, e: WorkEncounter): StaffMember {
        val skill = member.skills[e.trainedSkill] ?: SkillProgress(e.trainedSkill)
        val nextSkill = levelSkill(skill, e.skillXp)
        return member.copy(
            fatigue = (member.fatigue + e.fatigueDelta).coerceIn(0,100),
            stress = (member.stress + e.stressDelta).coerceIn(0,100),
            health = (member.health + e.healthDelta).coerceIn(0,100),
            reputation = (member.reputation + when (e.outcome) {
                EncounterOutcome.EXCELLENT -> 2
                EncounterOutcome.GOOD -> 1
                EncounterOutcome.INCIDENT -> -1
                else -> 0
            }).coerceIn(-100,100),
            skills = member.skills + (nextSkill.code to nextSkill),
        )
    }

    private fun maybeBuy(member: StaffMember, day: Int, random: Random): PurchaseOutcome {
        if (member.personalMoney < 5 || random.nextInt(100) >= 45) return PurchaseOutcome(member, null)
        val affordable = PURCHASES.filter { it.price <= member.personalMoney }
        if (affordable.isEmpty()) return PurchaseOutcome(member, null)
        val p = affordable.random(random)
        val item = InventoryItem("purchase-${member.id}-$day-${p.code}", p.name, tags = p.tags)
        return PurchaseOutcome(member.copy(personalMoney = member.personalMoney - p.price, inventory = member.inventory + item), PersonalPurchase(item, p.price, p.reason))
    }

    private fun diary(before: StaffMember, encounters: List<WorkEncounter>, purchase: PersonalPurchase?, after: StaffMember): String {
        val notable = encounters.maxByOrNull { diaryWeight(it.outcome) }
        val intro = when {
            encounters.isEmpty() -> "Сегодня смены фактически не получилось: посетителей не было."
            notable?.outcome == EncounterOutcome.INCIDENT -> "День оказался тяжёлым и к концу смены хотелось только тишины."
            notable?.outcome == EncounterOutcome.REFUSED -> "Сегодня пришлось особенно внимательно держать свои границы."
            encounters.any { it.outcome == EncounterOutcome.EXCELLENT } -> "Сегодня работа шла заметно лучше обычного, хотя лёгким день всё равно не назовёшь."
            encounters.any { it.outcome == EncounterOutcome.AWKWARD } -> "Смена вышла неровной: без серьёзной беды, но с неприятными моментами."
            else -> "День прошёл спокойно, но не был пустым — каждая встреча оставила свой след."
        }
        val encounterText = encounters.take(3).mapIndexed { index, e ->
            val order = when (index) { 0 -> "Первым"; 1 -> "Потом"; else -> "Позже" }
            val outcomeText = when (e.outcome) {
                EncounterOutcome.EXCELLENT -> "встреча прошла очень удачно"
                EncounterOutcome.GOOD -> "встреча прошла хорошо"
                EncounterOutcome.ROUTINE -> "всё прошло без особых проблем"
                EncounterOutcome.AWKWARD -> "встреча вышла неловкой"
                EncounterOutcome.REFUSED -> "пришлось отказать и обозначить границу"
                EncounterOutcome.INCIDENT -> "встречу пришлось закончить раньше"
            }
            "$order пришёл ${e.client.displayName}, ${e.client.archetype}: $outcomeText. ${e.summary}"
        }.joinToString(" ")
        val moneyText = if (encounters.isNotEmpty()) "За смену моя доля составила ${encounters.sumOf { it.staffCut }} галеонов." else ""
        val purchaseText = purchase?.let { "На свои деньги купила ${it.item.name} за ${it.price} галеонов — ${it.reason}." }.orEmpty()
        val conditionText = when {
            after.health < 50 -> "К концу дня здоровье уже беспокоит: ${after.health}/100; усталость ${after.fatigue}/100, стресс ${after.stress}/100."
            after.fatigue >= 80 -> "К вечеру усталость дошла до ${after.fatigue}/100, стресс — до ${after.stress}/100; завтра надо беречь силы."
            after.stress >= 70 -> "К концу смены стресс поднялся до ${after.stress}/100 при усталости ${after.fatigue}/100 — голова всё ещё гудит."
            else -> "К вечеру состояние терпимое: усталость ${after.fatigue}/100, стресс ${after.stress}/100, здоровье ${after.health}/100."
        }
        val loyaltyText = when {
            before.loyalty <= 35 -> "Я всё ещё не уверена, что хочу задерживаться здесь надолго."
            after.loyalty >= 75 -> "По крайней мере, здесь уже начинает появляться ощущение своего места."
            else -> "Посмотрим, каким окажется следующий день."
        }
        return listOf(intro, encounterText, moneyText, purchaseText, conditionText, loyaltyText).filter(String::isNotBlank).joinToString(" ")
    }

    private fun diaryWeight(outcome: EncounterOutcome): Int = when (outcome) {
        EncounterOutcome.INCIDENT -> 6
        EncounterOutcome.REFUSED -> 5
        EncounterOutcome.EXCELLENT -> 4
        EncounterOutcome.AWKWARD -> 3
        EncounterOutcome.GOOD -> 2
        EncounterOutcome.ROUTINE -> 1
    }

    private fun levelCharacter(start: Int, total: Int): Pair<Int, Int> {
        var level = start
        var xp = total
        while (true) {
            val threshold = 80 + (level - 1) * 35
            if (xp < threshold) return level to xp
            xp -= threshold
            level++
        }
    }

    private fun levelSkill(skill: SkillProgress, gain: Int): SkillProgress {
        var level = skill.level
        var xp = skill.xp + gain
        while (level < 15) {
            val threshold = 40 + (level - 1) * 20
            if (xp < threshold) break
            xp -= threshold
            level++
        }
        return skill.copy(level = level, xp = xp)
    }

    private fun skillFor(service: String) = when (service) {
        "conversation" -> "social"
        "massage" -> "bodywork"
        "roleplay" -> "roleplay"
        "private_intimacy" -> "intimacy"
        "arcane_fantasy" -> "arcane"
        else -> "hospitality"
    }

    private fun skillLabel(code: String) = when (code) {
        "hospitality" -> "сервис"
        "social" -> "общение"
        "bodywork" -> "телесная работа"
        "roleplay" -> "ролевые навыки"
        "intimacy" -> "интимность"
        "arcane" -> "магия"
        else -> code.replace('_', ' ')
    }

    private data class WorkingDayOutcome(val member: StaffMember, val report: StaffDayReport)
    private data class PlannedDayOutcome(val member: StaffMember, val report: StaffDayReport)
    private data class PurchaseOutcome(val member: StaffMember, val purchase: PersonalPurchase?)
    private data class ClientArchetype(val title: String, val names: List<String>, val age: IntRange, val wealth: Int)
    private data class PurchaseTemplate(val code: String, val name: String, val price: Long, val tags: Set<String>, val reason: String)

    private class RecordFactory(private val day: Int) {
        private var eventSeq = 0
        private var memorySeq = 0
        fun event(type: String, summary: String, payload: String = ""): WorldEvent {
            val seq = eventSeq++
            return WorldEvent("event-$day-$seq-$type", day, type, summary, payload, timestamp(seq))
        }
        fun memory(staffId: String, category: String, summary: String, importance: Int): StaffMemory {
            val seq = memorySeq++
            return StaffMemory("memory-$day-$seq-$staffId-$category", staffId, day, category, summary, importance, timestamp(200 + seq))
        }
        fun timestamp(order: Int): Long = SIM_EPOCH + day * DAY_MS + order
    }

    companion object {
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val SIM_EPOCH = 946684800000L
        private const val DAY_MS = 86_400_000L
        private val SERVICES = listOf("conversation", "massage", "roleplay", "private_intimacy", "arcane_fantasy")
        private val TRAINING_SKILLS = listOf("hospitality", "social", "bodywork", "roleplay", "intimacy", "arcane")
        private val PRICES = mapOf(
            "conversation" to 4L,
            "massage" to 6L,
            "roleplay" to 8L,
            "private_intimacy" to 9L,
            "arcane_fantasy" to 11L,
        )
        private val CLIENTS = listOf(
            ClientArchetype("городской писарь", listOf("Господин Рен", "Писарь Лем", "Тихий Орт"), 23..48, 35),
            ClientArchetype("караванный торговец", listOf("Старший Даг", "Купец Варо", "Хозяин Сел"), 28..58, 58),
            ClientArchetype("ремесленник", listOf("Мастер Корн", "Литейщик Пел", "Стекольщик Дор"), 25..52, 42),
            ClientArchetype("чиновник инкогнито", listOf("Серый господин", "Посетитель в перчатках", "Человек без герба"), 31..61, 72),
            ClientArchetype("маг-практик", listOf("Адепт Вир", "Практик Эл", "Ученик Тар"), 21..40, 50),
        )
        private val PURCHASES = listOf(
            PurchaseTemplate("ribbon", "новую ленту для волос", 5, setOf("clothing", "personal"), "хотела выглядеть аккуратнее"),
            PurchaseTemplate("earrings", "простые медные серьги", 7, setOf("jewelry", "personal"), "решила порадовать себя"),
            PurchaseTemplate("perfume", "маленький флакон духов", 9, setOf("cosmetic", "personal"), "понравился запах на рынке"),
            PurchaseTemplate("boots", "удобные рабочие сапоги", 12, setOf("clothing", "practical"), "старые начали натирать"),
        )
    }
}
