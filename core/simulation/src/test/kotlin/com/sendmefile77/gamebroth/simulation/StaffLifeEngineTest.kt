package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.DailyReport
import com.sendmefile77.gamebroth.model.EstablishmentState
import com.sendmefile77.gamebroth.model.GameState
import com.sendmefile77.gamebroth.model.SkillProgress
import com.sendmefile77.gamebroth.model.StaffDayReport
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.StaffRequest
import com.sendmefile77.gamebroth.model.StaffRequestKind
import com.sendmefile77.gamebroth.model.StaffRequestStatus
import com.sendmefile77.gamebroth.model.StaffStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffLifeEngineTest {
    private val engine = StaffLifeEngine()

    @Test
    fun exhaustedStaffCreatesDayOffRequest() {
        val member = StaffMember(
            id = "s1", name = "Мира", species = "человек", ageYears = 26,
            fatigue = 82, stress = 30, skills = mapOf("social" to SkillProgress("social", 2)),
        )
        val state = GameState.newGame(42).copy(currentDay = 4, staff = listOf(member))
        val report = DailyReport(
            day = 3,
            staffReports = listOf(StaffDayReport(3, member.id, member.name, 1, 1, emptyList(), 0, 0, incident = "План дня: отдых.")),
            grossRevenue = 0, upkeep = 5, treasuryAfter = 10, debtDelta = 0, createdAtEpochMs = 1,
        )

        val result = engine.afterDay(state, report, emptyList())
        val request = result.requestUpdates.single()
        assertEquals(StaffRequestKind.DAY_OFF, request.kind)
        assertEquals(StaffRequestStatus.PENDING, request.status)
        assertEquals(4, request.expiresDay)
    }

    @Test
    fun acceptingDayOffLocksInRestAndRaisesLoyalty() {
        val member = StaffMember(id = "s1", name = "Мира", species = "человек", ageYears = 26, loyalty = 40, stress = 50)
        val state = GameState.newGame(7).copy(currentDay = 5, staff = listOf(member))
        val request = request(kind = StaffRequestKind.DAY_OFF, loyaltyAccept = 5, loyaltyRefuse = -4)

        val result = engine.resolve(state, request, accept = true)
        val after = result.state.staff.single()
        assertEquals(45, after.loyalty)
        assertEquals(StaffStatus.RESTING, after.status)
        assertEquals(StaffRequestStatus.ACCEPTED, result.request.status)
    }

    @Test
    fun acceptingBonusTransfersTreasuryToPersonalMoney() {
        val member = StaffMember(id = "s1", name = "Мира", species = "человек", ageYears = 26, loyalty = 25, personalMoney = 2)
        val state = GameState.newGame(7).copy(
            currentDay = 5,
            establishment = EstablishmentState(treasury = 12),
            staff = listOf(member),
        )
        val request = request(kind = StaffRequestKind.BONUS, cost = 5, loyaltyAccept = 7, loyaltyRefuse = -5)

        val result = engine.resolve(state, request, accept = true)
        assertEquals(7, result.state.establishment.treasury)
        assertEquals(7, result.state.staff.single().personalMoney)
        assertEquals(32, result.state.staff.single().loyalty)
    }

    @Test
    fun ignoredRequestExpiresAndHurtsLoyalty() {
        val member = StaffMember(id = "s1", name = "Мира", species = "человек", ageYears = 26, loyalty = 40)
        val state = GameState.newGame(7).copy(currentDay = 6, staff = listOf(member))
        val old = request(kind = StaffRequestKind.DAY_OFF, loyaltyAccept = 5, loyaltyRefuse = -4).copy(expiresDay = 5)
        val report = DailyReport(
            day = 5,
            staffReports = listOf(StaffDayReport(5, member.id, member.name, 1, 1, emptyList(), 0, 0)),
            grossRevenue = 0, upkeep = 5, treasuryAfter = 10, debtDelta = 0, createdAtEpochMs = 1,
        )

        val result = engine.afterDay(state, report, listOf(old))
        assertTrue(result.requestUpdates.any { it.id == old.id && it.status == StaffRequestStatus.EXPIRED })
        assertTrue(result.state.staff.single().loyalty <= 36)
    }

    @Test
    fun criticallyDisloyalStaffLeaves() {
        val member = StaffMember(id = "s1", name = "Мира", species = "человек", ageYears = 26, loyalty = 7)
        val state = GameState.newGame(7).copy(currentDay = 3, staff = listOf(member))
        val report = DailyReport(
            day = 2,
            staffReports = listOf(StaffDayReport(2, member.id, member.name, 1, 1, emptyList(), 0, 0)),
            grossRevenue = 0, upkeep = 5, treasuryAfter = 10, debtDelta = 0, createdAtEpochMs = 1,
        )

        val result = engine.afterDay(state, report, emptyList())
        assertEquals(StaffStatus.LEFT, result.state.staff.single().status)
        assertTrue(result.events.any { it.type == "STAFF_LEFT" })
    }

    private fun request(
        kind: StaffRequestKind,
        cost: Long = 0,
        loyaltyAccept: Int,
        loyaltyRefuse: Int,
    ) = StaffRequest(
        id = "request-test-s1-${kind.name}",
        staffId = "s1",
        staffName = "Мира",
        createdDay = 4,
        expiresDay = 5,
        kind = kind,
        title = "Тестовая просьба",
        body = "Тестовая просьба сотрудницы.",
        cost = cost,
        loyaltyOnAccept = loyaltyAccept,
        loyaltyOnRefuse = loyaltyRefuse,
        stressOnAccept = -2,
        stressOnRefuse = 3,
    )
}
