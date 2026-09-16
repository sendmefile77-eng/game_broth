package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstablishmentEngineTest {
    private val engine = EstablishmentEngine()

    @Test fun premiumPricingRaisesBusinessRevenue() {
        val standard = before(pricing = PricingPolicy.STANDARD)
        val premium = before(pricing = PricingPolicy.PREMIUM)
        val standardResult = engine.applyDay(standard, coreResult(standard))
        val premiumResult = engine.applyDay(premium, coreResult(premium))

        assertTrue(premiumResult.report.grossRevenue > standardResult.report.grossRevenue)
        assertTrue(premiumResult.state.establishment.treasury > standardResult.state.establishment.treasury)
    }

    @Test fun intenseWorkloadTradesConditionForMoneyAndHeat() {
        val normal = before(workload = WorkloadPolicy.NORMAL)
        val intense = before(workload = WorkloadPolicy.INTENSE)
        val normalResult = engine.applyDay(normal, coreResult(normal))
        val intenseResult = engine.applyDay(intense, coreResult(intense))

        assertTrue(intenseResult.report.grossRevenue >= normalResult.report.grossRevenue)
        assertTrue(intenseResult.state.staff.single().fatigue > normalResult.state.staff.single().fatigue)
        assertTrue(intenseResult.state.establishment.heat >= normalResult.state.establishment.heat)
        assertTrue(intenseResult.events.any { it.type == "INTENSE_WORKLOAD" })
    }

    @Test fun secrecyCanAbsorbIncidentHeat() {
        val exposed = before(secrecy = 0)
        val hidden = before(secrecy = 70)
        val exposedResult = engine.applyDay(exposed, coreResult(exposed, outcome = EncounterOutcome.INCIDENT))
        val hiddenResult = engine.applyDay(hidden, coreResult(hidden, outcome = EncounterOutcome.INCIDENT))

        assertTrue(hiddenResult.state.establishment.heat < exposedResult.state.establishment.heat)
    }

    @Test fun creditorInterestIsAddedEveryThirdDay() {
        val state = before(day = 3, debt = 20)
        val result = engine.applyDay(state, coreResult(state))
        assertTrue(result.report.debtDelta >= 2)
        assertTrue(result.events.any { it.type == "CREDITOR_PRESSURE" })
    }

    @Test fun managementActionsSpendRealTreasury() {
        val state = before(treasury = 30, debt = 10)
        val repaid = engine.repayDebt(state, 6).state
        assertEquals(24L, repaid.establishment.treasury)
        assertEquals(4L, repaid.establishment.debt)

        val luxury = engine.upgradeLuxury(repaid).state
        assertEquals(10, luxury.establishment.luxury)
        assertTrue(luxury.establishment.treasury < repaid.establishment.treasury)
    }

    private fun before(
        day: Int = 1,
        treasury: Long = 10,
        debt: Long = 0,
        secrecy: Int = 0,
        pricing: PricingPolicy = PricingPolicy.STANDARD,
        workload: WorkloadPolicy = WorkloadPolicy.NORMAL,
    ) = GameState(
        worldSeed = 1L,
        currentDay = day,
        establishment = EstablishmentState(
            treasury = treasury,
            debt = debt,
            secrecy = secrecy,
            pricingPolicy = pricing,
            workloadPolicy = workload,
        ),
        staff = listOf(staff()),
    )

    private fun coreResult(before: GameState, outcome: EncounterOutcome = EncounterOutcome.GOOD): DayResult {
        val member = before.staff.single()
        val encounter = WorkEncounter(
            id = "e-${before.currentDay}",
            day = before.currentDay,
            staffId = member.id,
            client = ClientProfile("c", "Клиент", "ремесленник", 30, 50, 50, 50),
            serviceCode = "massage",
            outcome = outcome,
            summary = "test",
            grossRevenue = 12,
            staffCut = 3,
            businessCut = 9,
            trainedSkill = "bodywork",
            skillXp = 5,
            fatigueDelta = 5,
            stressDelta = 1,
            healthDelta = 0,
        )
        val report = StaffDayReport(before.currentDay, member.id, member.name, 1, 1, listOf(encounter), 9, 3)
        val coreState = before.copy(
            currentDay = before.currentDay + 1,
            establishment = before.establishment.copy(
                treasury = before.establishment.treasury + 9 - 5,
                heat = before.establishment.heat + if (outcome == EncounterOutcome.INCIDENT) 1 else 0,
            ),
            staff = listOf(member.copy(fatigue = 10, stress = 5)),
        )
        return DayResult(
            state = coreState,
            ledger = listOf(LedgerEntry("SESSION:e", 9, "test"), LedgerEntry("UPKEEP", -5, "test")),
            events = emptyList(),
            memories = emptyList(),
            report = DailyReport(before.currentDay, listOf(report), 9, 5, coreState.establishment.treasury, 0, 1000L),
        )
    }

    private fun staff() = StaffMember(
        id = "s1",
        name = "Ада",
        species = "human",
        ageYears = 25,
        skills = mapOf("bodywork" to SkillProgress("bodywork", 2, 0)),
    )
}
