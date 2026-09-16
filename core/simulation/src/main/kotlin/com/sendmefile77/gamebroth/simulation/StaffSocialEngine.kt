package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import kotlin.math.absoluteValue

data class StaffSocialResult(
    val state: GameState,
    val events: List<WorldEvent>,
    val memories: List<StaffMemory>,
)

class StaffSocialEngine {
    fun afterDay(state: GameState, report: DailyReport): StaffSocialResult {
        require(state.currentDay == report.day + 1)
        val events = mutableListOf<WorldEvent>()
        val memories = mutableListOf<StaffMemory>()
        val active = state.staff.filter { it.status != StaffStatus.LEFT }
        val staffById = state.staff.associateBy { it.id }
        val reportByStaff = report.staffReports.associateBy { it.staffId }
        val oldRelations = state.staffRelations.associateBy { it.firstStaffId to it.secondStaffId }.toMutableMap()

        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]
                val pair = normalizedPair(a.id, b.id)
                val previous = oldRelations[pair] ?: StaffRelation(
                    firstStaffId = pair.first,
                    secondStaffId = pair.second,
                    affinity = initialAffinity(a, b),
                    tension = 0,
                    updatedDay = report.day,
                )
                val updated = updateRelation(previous, a, b, reportByStaff[a.id], reportByStaff[b.id], state.worldSeed, report.day)
                oldRelations[pair] = updated

                if (previous.affinity < FRIEND_THRESHOLD && updated.affinity >= FRIEND_THRESHOLD) {
                    events += event(report.day, "STAFF_BOND", "${a.name} и ${b.name} заметно сблизились.", "a=${a.id};b=${b.id};affinity=${updated.affinity}")
                    memories += relationMemory(a.id, report.day, "Мы с ${b.name} стали заметно ближе.", 3, b.id)
                    memories += relationMemory(b.id, report.day, "Мы с ${a.name} стали заметно ближе.", 3, a.id)
                }
                if (previous.tension < CONFLICT_THRESHOLD && updated.tension >= CONFLICT_THRESHOLD) {
                    events += event(report.day, "STAFF_CONFLICT", "Между ${a.name} и ${b.name} накопилось серьёзное напряжение.", "a=${a.id};b=${b.id};tension=${updated.tension}")
                    memories += relationMemory(a.id, report.day, "С ${b.name} становится всё тяжелее находиться рядом.", 4, b.id)
                    memories += relationMemory(b.id, report.day, "С ${a.name} становится всё тяжелее находиться рядом.", 4, a.id)
                }
            }
        }

        var staff = state.staff
        val relationEffects = oldRelations.values.groupByPairEffects(active.map { it.id }.toSet())
        staff = staff.map { member ->
            val effect = relationEffects[member.id] ?: 0
            if (effect == 0 || member.status == StaffStatus.LEFT) member
            else member.copy(stress = (member.stress + effect).coerceIn(0, 100))
        }

        val updatedGoals = mutableListOf<StaffGoal>()
        val activeGoalByStaff = state.staffGoals.filter { it.status == StaffGoalStatus.ACTIVE }.associateBy { it.staffId }
        val historical = state.staffGoals.filter { it.status != StaffGoalStatus.ACTIVE }.toMutableList()
        var workingStaff = staff

        active.forEachIndexed { index, originalMember ->
            val member = workingStaff.first { it.id == originalMember.id }
            val oldGoal = activeGoalByStaff[member.id]
            if (oldGoal != null) {
                val progress = goalProgress(oldGoal, member)
                when {
                    progress >= oldGoal.target -> {
                        val completed = oldGoal.copy(progress = progress, status = StaffGoalStatus.COMPLETED)
                        historical += completed
                        workingStaff = workingStaff.map { if (it.id == member.id) it.copy(loyalty = (it.loyalty + 2).coerceAtMost(100)) else it }
                        events += event(report.day, "STAFF_GOAL_COMPLETED", "${member.name} достигла личной цели: ${oldGoal.title}.", "staffId=${member.id};goalId=${oldGoal.id}")
                        memories += goalMemory(member.id, report.day, "Я достигла цели: ${oldGoal.title}.", 4, oldGoal.id)
                    }
                    state.currentDay > oldGoal.deadlineDay -> {
                        val failed = oldGoal.copy(progress = progress, status = StaffGoalStatus.FAILED)
                        historical += failed
                        workingStaff = workingStaff.map { if (it.id == member.id) it.copy(loyalty = (it.loyalty - 1).coerceAtLeast(0), stress = (it.stress + 2).coerceAtMost(100)) else it }
                        events += event(report.day, "STAFF_GOAL_FAILED", "${member.name} не успела достичь личной цели: ${oldGoal.title}.", "staffId=${member.id};goalId=${oldGoal.id}")
                        memories += goalMemory(member.id, report.day, "Моя цель сорвалась: ${oldGoal.title}.", 3, oldGoal.id)
                    }
                    else -> updatedGoals += oldGoal.copy(progress = progress)
                }
            }
        }

        val stillActive = updatedGoals.map { it.staffId }.toSet()
        workingStaff.filter { it.status != StaffStatus.LEFT }.forEachIndexed { index, member ->
            if (member.id !in stillActive) {
                val goal = buildGoal(state.copy(staff = workingStaff), member, report.day, index)
                updatedGoals += goal
                events += event(report.day, "STAFF_GOAL_CREATED", "${member.name}: новая личная цель — ${goal.title}.", "staffId=${member.id};goalId=${goal.id};deadline=${goal.deadlineDay}")
                memories += goalMemory(member.id, report.day, "Я поставила себе цель: ${goal.title}.", 2, goal.id)
            }
        }

        val allRelations = oldRelations.values.sortedWith(compareBy<StaffRelation> { it.firstStaffId }.thenBy { it.secondStaffId })
        val next = state.copy(
            schemaVersion = maxOf(state.schemaVersion, 5),
            staff = workingStaff,
            staffRelations = allRelations,
            staffGoals = (historical + updatedGoals).distinctBy { it.id },
        )
        return StaffSocialResult(next, events, memories)
    }

    private fun updateRelation(
        previous: StaffRelation,
        a: StaffMember,
        b: StaffMember,
        aReport: StaffDayReport?,
        bReport: StaffDayReport?,
        seed: Long,
        day: Int,
    ): StaffRelation {
        var affinityDelta = 0
        var tensionDelta = -1
        val aMode = dayMode(aReport)
        val bMode = dayMode(bReport)
        if (aMode == bMode && aMode != "work") affinityDelta += 2
        if (aReport?.encounters?.any { it.outcome == EncounterOutcome.EXCELLENT } == true &&
            bReport?.encounters?.any { it.outcome == EncounterOutcome.EXCELLENT } == true) affinityDelta += 1
        if (a.traits.intersect(b.traits).isNotEmpty()) affinityDelta += 1
        if (a.species == b.species) affinityDelta += 1

        val combinedStress = a.stress + b.stress
        val roll = pairRoll(seed, day, a.id, b.id, 11)
        if (roll == 0) affinityDelta += 2
        if (roll == 1 && combinedStress >= 120) tensionDelta += 4
        if (aReport?.encounters?.any { it.outcome == EncounterOutcome.INCIDENT } == true && b.stress >= 65) tensionDelta += 2
        if (bReport?.encounters?.any { it.outcome == EncounterOutcome.INCIDENT } == true && a.stress >= 65) tensionDelta += 2
        if (previous.affinity >= 45) tensionDelta -= 1
        if (previous.tension >= 55) affinityDelta -= 1

        return previous.copy(
            affinity = (previous.affinity + affinityDelta).coerceIn(-100, 100),
            tension = (previous.tension + tensionDelta).coerceIn(0, 100),
            updatedDay = day,
        )
    }

    private fun Collection<StaffRelation>.groupByPairEffects(activeIds: Set<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        forEach { relation ->
            if (relation.firstStaffId !in activeIds || relation.secondStaffId !in activeIds) return@forEach
            val effect = when {
                relation.tension >= 70 -> 2
                relation.tension >= 50 -> 1
                relation.affinity >= 65 -> -1
                else -> 0
            }
            if (effect != 0) {
                result[relation.firstStaffId] = (result[relation.firstStaffId] ?: 0) + effect
                result[relation.secondStaffId] = (result[relation.secondStaffId] ?: 0) + effect
            }
        }
        return result
    }

    private fun buildGoal(state: GameState, member: StaffMember, completedDay: Int, ordinal: Int): StaffGoal {
        val weakest = member.skills.values.minByOrNull { it.level }
        val kind = when {
            member.health < 70 -> StaffGoalKind.RECOVER
            weakest != null && weakest.level <= 3 -> StaffGoalKind.TRAIN_SKILL
            member.personalMoney < 12 -> StaffGoalKind.EARN_MONEY
            member.loyalty < 70 -> StaffGoalKind.BUILD_LOYALTY
            pairRoll(state.worldSeed, completedDay, member.id, "goal", 2) == 0 -> StaffGoalKind.EARN_MONEY
            else -> StaffGoalKind.TRAIN_SKILL
        }
        val currentDay = state.currentDay
        val goalId = "goal-$completedDay-${member.id}-${kind.name.lowercase()}-$ordinal"
        return when (kind) {
            StaffGoalKind.RECOVER -> StaffGoal(goalId, member.id, kind, "восстановить здоровье до 85", 85, member.health, null, completedDay, currentDay + 5)
            StaffGoalKind.EARN_MONEY -> {
                val target = (member.personalMoney + 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                StaffGoal(goalId, member.id, kind, "накопить $target личных галеонов", target, member.personalMoney.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), null, completedDay, currentDay + 6)
            }
            StaffGoalKind.TRAIN_SKILL -> {
                val skill = weakest ?: SkillProgress("hospitality")
                val target = (skill.level + 1).coerceAtMost(15)
                StaffGoal(goalId, member.id, kind, "поднять «${skillLabel(skill.code)}» до уровня $target", target, skill.level, skill.code, completedDay, currentDay + 7)
            }
            StaffGoalKind.BUILD_LOYALTY -> {
                val target = (member.loyalty + 15).coerceAtMost(80)
                StaffGoal(goalId, member.id, kind, "довести доверие к заведению до $target", target, member.loyalty, null, completedDay, currentDay + 7)
            }
        }
    }

    private fun goalProgress(goal: StaffGoal, member: StaffMember): Int = when (goal.kind) {
        StaffGoalKind.RECOVER -> member.health
        StaffGoalKind.EARN_MONEY -> member.personalMoney.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        StaffGoalKind.TRAIN_SKILL -> member.skills[goal.targetCode]?.level ?: 0
        StaffGoalKind.BUILD_LOYALTY -> member.loyalty
    }

    private fun initialAffinity(a: StaffMember, b: StaffMember): Int {
        var value = 0
        value += a.traits.intersect(b.traits).size * 3
        if (a.species == b.species) value += 4
        return value.coerceIn(-20, 20)
    }

    private fun dayMode(report: StaffDayReport?): String {
        val note = report?.incident.orEmpty().lowercase()
        return when {
            note.contains("обучен") -> "training"
            note.contains("отдых") || note.contains("восстанов") -> "rest"
            else -> "work"
        }
    }

    private fun normalizedPair(a: String, b: String): Pair<String, String> = if (a < b) a to b else b to a

    private fun pairRoll(seed: Long, day: Int, a: String, b: String, modulus: Int): Int {
        val mixed = seed xor a.hashCode().toLong() xor (b.hashCode().toLong() shl 1) xor (day.toLong() * GOLDEN_GAMMA)
        return ((mixed xor (mixed ushr 32)).toInt().absoluteValue % modulus)
    }

    private fun event(day: Int, type: String, summary: String, payload: String) = WorldEvent(
        id = "social-event-$day-$type-${payload.hashCode().toUInt()}", day = day, type = type, summary = summary, payload = payload,
        createdAtEpochMs = SIM_EPOCH_MS + day * DAY_MS + (payload.hashCode().absoluteValue % 50_000),
    )

    private fun relationMemory(staffId: String, day: Int, summary: String, importance: Int, otherId: String) = StaffMemory(
        id = "social-memory-$day-$staffId-$otherId-${summary.hashCode().toUInt()}", staffId = staffId, day = day,
        category = "relationship", summary = summary, importance = importance, createdAtEpochMs = SIM_EPOCH_MS + day * DAY_MS + 60_000,
    )

    private fun goalMemory(staffId: String, day: Int, summary: String, importance: Int, goalId: String) = StaffMemory(
        id = "goal-memory-$day-$staffId-$goalId-${summary.hashCode().toUInt()}", staffId = staffId, day = day,
        category = "goal", summary = summary, importance = importance, createdAtEpochMs = SIM_EPOCH_MS + day * DAY_MS + 70_000,
    )

    private fun skillLabel(code: String): String = when (code) {
        "hospitality" -> "Сервис"
        "social" -> "Общение"
        "bodywork" -> "Телесная работа"
        "roleplay" -> "Роли"
        "intimacy" -> "Интимность"
        "arcane" -> "Магия"
        else -> code.replace('_', ' ')
    }

    companion object {
        private const val FRIEND_THRESHOLD = 35
        private const val CONFLICT_THRESHOLD = 45
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val SIM_EPOCH_MS = 1_800_000_000_000L
        private const val DAY_MS = 86_400_000L
    }
}
