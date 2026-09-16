package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import kotlin.math.max
import kotlin.math.roundToLong

data class ManagementDecision(
    val state: GameState,
    val event: WorldEvent,
)

class EstablishmentEngine {
    fun applyDay(before: GameState, core: DayResult): DayResult {
        require(before.currentDay == core.report.day)
        val day = core.report.day
        val establishment = before.establishment
        val revenueFactor = pricingFactor(establishment.pricingPolicy) *
            workloadRevenueFactor(establishment.workloadPolicy) *
            (1.0 + establishment.luxury / 500.0)

        val adjustedStaffReports = core.report.staffReports.map { report ->
            val adjustedEncounters = report.encounters.map { encounter ->
                if (encounter.businessCut <= 0L) encounter
                else {
                    val businessCut = max(1L, (encounter.businessCut * revenueFactor).roundToLong())
                    encounter.copy(
                        grossRevenue = encounter.staffCut + businessCut,
                        businessCut = businessCut,
                    )
                }
            }
            report.copy(
                encounters = adjustedEncounters,
                businessRevenue = adjustedEncounters.sumOf { it.businessCut },
                personalRevenue = adjustedEncounters.sumOf { it.staffCut },
            )
        }
        val businessRevenue = adjustedStaffReports.sumOf { it.businessRevenue }
        val baseHeatDelta = adjustedStaffReports.sumOf { sr -> sr.encounters.count { it.outcome == EncounterOutcome.INCIDENT } }
        val workloadHeat = if (establishment.workloadPolicy == WorkloadPolicy.INTENSE && adjustedStaffReports.any { it.encounters.isNotEmpty() }) 1 else 0
        val secrecyShield = when {
            establishment.secrecy >= 70 -> 2
            establishment.secrecy >= 30 -> 1
            else -> 0
        }
        var heatAfter = (establishment.heat + (baseHeatDelta + workloadHeat - secrecyShield).coerceAtLeast(0)).coerceIn(0, 100)
        if (baseHeatDelta == 0 && workloadHeat == 0 && establishment.secrecy >= 40 && heatAfter > 0) heatAfter--

        val cityPressureCost = if (heatAfter >= 60) 2L + heatAfter / 25L else 0L
        val interest = if (establishment.debt > 0 && day % 3 == 0) max(1L, establishment.debt / 10L) else 0L
        val upkeep = core.report.upkeep + cityPressureCost
        val cashAfter = establishment.treasury + businessRevenue - upkeep
        val treasuryAfter = cashAfter.coerceAtLeast(0)
        val shortfall = (-cashAfter).coerceAtLeast(0)
        val debtDelta = shortfall + interest

        var reputationDelta = when (establishment.pricingPolicy) {
            PricingPolicy.BUDGET -> if (businessRevenue > 0) 1 else 0
            PricingPolicy.STANDARD -> 0
            PricingPolicy.PREMIUM -> if (establishment.luxury >= 30 && businessRevenue > 0) 1 else 0
        }
        if (establishment.luxury >= 50 && businessRevenue > 0) reputationDelta++
        if (heatAfter >= 80) reputationDelta--

        val luxuryStressRelief = (establishment.luxury / 25).coerceAtMost(4)
        val workingIds = adjustedStaffReports.filter { it.encounters.isNotEmpty() }.map { it.staffId }.toSet()
        val adjustedStaff = core.state.staff.map { member ->
            if (member.status == StaffStatus.LEFT) member
            else {
                val workloadFatigue = if (member.id in workingIds) when (establishment.workloadPolicy) {
                    WorkloadPolicy.GENTLE -> -3
                    WorkloadPolicy.NORMAL -> 0
                    WorkloadPolicy.INTENSE -> 5
                } else 0
                val workloadStress = if (member.id in workingIds) when (establishment.workloadPolicy) {
                    WorkloadPolicy.GENTLE -> -2
                    WorkloadPolicy.NORMAL -> 0
                    WorkloadPolicy.INTENSE -> 3
                } else 0
                member.copy(
                    fatigue = (member.fatigue + workloadFatigue).coerceIn(0, 100),
                    stress = (member.stress + workloadStress - luxuryStressRelief).coerceIn(0, 100),
                )
            }
        }

        val nextEstablishment = core.state.establishment.copy(
            treasury = treasuryAfter,
            debt = establishment.debt + debtDelta,
            publicReputation = (core.state.establishment.publicReputation + reputationDelta).coerceIn(-100, 100),
            heat = heatAfter,
            pricingPolicy = establishment.pricingPolicy,
            workloadPolicy = establishment.workloadPolicy,
        )
        val nextState = core.state.copy(
            schemaVersion = maxOf(core.state.schemaVersion, 6),
            establishment = nextEstablishment,
            staff = adjustedStaff,
        )
        val report = core.report.copy(
            staffReports = adjustedStaffReports,
            grossRevenue = businessRevenue,
            upkeep = upkeep,
            treasuryAfter = treasuryAfter,
            debtDelta = debtDelta,
        )
        val events = core.events.toMutableList()
        if (interest > 0) events += managementEvent(day, "CREDITOR_PRESSURE", "Кредиторы начислили $interest г. к долгу.", "interest=$interest;debt=${nextEstablishment.debt}")
        if (cityPressureCost > 0) events += managementEvent(day, "CITY_PRESSURE", "Высокое внимание города обошлось заведению в $cityPressureCost г.", "heat=$heatAfter;cost=$cityPressureCost")
        if (establishment.workloadPolicy == WorkloadPolicy.INTENSE && workingIds.isNotEmpty()) {
            events += managementEvent(day, "INTENSE_WORKLOAD", "Интенсивная нагрузка дала больше выручки, но сильнее вымотала персонал.", "staff=${workingIds.size}")
        }
        val ledger = core.ledger.toMutableList()
        val revenueAdjustment = businessRevenue - core.report.grossRevenue
        if (revenueAdjustment != 0L) ledger += LedgerEntry("MANAGEMENT_MARGIN", revenueAdjustment, "Эффект цен, нагрузки и уровня комфорта")
        if (cityPressureCost > 0) ledger += LedgerEntry("CITY_PRESSURE", -cityPressureCost, "Издержки из-за внимания города")
        return core.copy(state = nextState, report = report, events = events, ledger = ledger)
    }

    fun setPricing(state: GameState, policy: PricingPolicy): ManagementDecision {
        val next = state.copy(schemaVersion = maxOf(state.schemaVersion, 6), establishment = state.establishment.copy(pricingPolicy = policy))
        return ManagementDecision(next, decisionEvent(state.currentDay, "PRICING_CHANGED", "Ценовая политика: ${pricingLabel(policy)}.", "policy=${policy.name}"))
    }

    fun setWorkload(state: GameState, policy: WorkloadPolicy): ManagementDecision {
        val next = state.copy(schemaVersion = maxOf(state.schemaVersion, 6), establishment = state.establishment.copy(workloadPolicy = policy))
        return ManagementDecision(next, decisionEvent(state.currentDay, "WORKLOAD_CHANGED", "Нагрузка персонала: ${workloadLabel(policy)}.", "policy=${policy.name}"))
    }

    fun repayDebt(state: GameState, requested: Long): ManagementDecision {
        require(requested > 0) { "Repayment must be positive" }
        val amount = minOf(requested, state.establishment.treasury, state.establishment.debt)
        require(amount > 0) { "Nothing to repay" }
        val next = state.copy(establishment = state.establishment.copy(
            treasury = state.establishment.treasury - amount,
            debt = state.establishment.debt - amount,
        ))
        return ManagementDecision(next, decisionEvent(state.currentDay, "DEBT_REPAID", "Погашено $amount г. долга.", "amount=$amount;debt=${next.establishment.debt}"))
    }

    fun upgradeLuxury(state: GameState): ManagementDecision {
        require(state.establishment.luxury < 100) { "Luxury already maxed" }
        val cost = 6L + (state.establishment.luxury / 10) * 3L
        require(state.establishment.treasury >= cost) { "Not enough treasury" }
        val nextValue = (state.establishment.luxury + 10).coerceAtMost(100)
        val next = state.copy(establishment = state.establishment.copy(
            treasury = state.establishment.treasury - cost,
            luxury = nextValue,
        ))
        return ManagementDecision(next, decisionEvent(state.currentDay, "LUXURY_UPGRADED", "Комфорт заведения вырос до $nextValue/100 за $cost г.", "cost=$cost;luxury=$nextValue"))
    }

    fun upgradeSecrecy(state: GameState): ManagementDecision {
        require(state.establishment.secrecy < 100) { "Secrecy already maxed" }
        val cost = 7L + (state.establishment.secrecy / 10) * 3L
        require(state.establishment.treasury >= cost) { "Not enough treasury" }
        val nextValue = (state.establishment.secrecy + 10).coerceAtMost(100)
        val next = state.copy(establishment = state.establishment.copy(
            treasury = state.establishment.treasury - cost,
            secrecy = nextValue,
            heat = (state.establishment.heat - 2).coerceAtLeast(0),
        ))
        return ManagementDecision(next, decisionEvent(state.currentDay, "SECRECY_UPGRADED", "Скрытность заведения выросла до $nextValue/100 за $cost г.", "cost=$cost;secrecy=$nextValue"))
    }

    fun layLow(state: GameState): ManagementDecision {
        require(state.establishment.heat > 0) { "No heat to reduce" }
        val cost = 3L + state.establishment.heat / 20L
        require(state.establishment.treasury >= cost) { "Not enough treasury" }
        val reduction = 10 + state.establishment.secrecy / 10
        val nextHeat = (state.establishment.heat - reduction).coerceAtLeast(0)
        val next = state.copy(establishment = state.establishment.copy(
            treasury = state.establishment.treasury - cost,
            heat = nextHeat,
        ))
        return ManagementDecision(next, decisionEvent(state.currentDay, "LAID_LOW", "Заведение залегло на дно: внимание города ${state.establishment.heat}→$nextHeat за $cost г.", "cost=$cost;heat=$nextHeat"))
    }

    private fun pricingFactor(policy: PricingPolicy) = when (policy) {
        PricingPolicy.BUDGET -> 0.90
        PricingPolicy.STANDARD -> 1.0
        PricingPolicy.PREMIUM -> 1.20
    }

    private fun workloadRevenueFactor(policy: WorkloadPolicy) = when (policy) {
        WorkloadPolicy.GENTLE -> 0.90
        WorkloadPolicy.NORMAL -> 1.0
        WorkloadPolicy.INTENSE -> 1.15
    }

    private fun pricingLabel(policy: PricingPolicy) = when (policy) {
        PricingPolicy.BUDGET -> "доступные цены"
        PricingPolicy.STANDARD -> "обычные цены"
        PricingPolicy.PREMIUM -> "премиальные цены"
    }

    private fun workloadLabel(policy: WorkloadPolicy) = when (policy) {
        WorkloadPolicy.GENTLE -> "бережная"
        WorkloadPolicy.NORMAL -> "обычная"
        WorkloadPolicy.INTENSE -> "интенсивная"
    }

    private fun managementEvent(day: Int, type: String, summary: String, payload: String) = WorldEvent(
        id = "management-day-$day-$type", day = day, type = type, summary = summary, payload = payload,
        createdAtEpochMs = SIM_EPOCH + day * DAY_MS + 80_000 + type.hashCode().and(Int.MAX_VALUE) % 10_000,
    )

    private fun decisionEvent(day: Int, type: String, summary: String, payload: String) = WorldEvent(
        id = "management-decision-$day-$type-${payload.hashCode().toUInt()}", day = day, type = type, summary = summary, payload = payload,
        createdAtEpochMs = System.currentTimeMillis(),
    )

    companion object {
        private const val SIM_EPOCH = 946684800000L
        private const val DAY_MS = 86_400_000L
    }
}
