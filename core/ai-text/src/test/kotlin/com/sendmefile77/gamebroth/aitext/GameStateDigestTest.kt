package com.sendmefile77.gamebroth.aitext

import com.sendmefile77.gamebroth.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateDigestTest {
    @Test
    fun digestIncludesStatusGoalsRelationsAndManagementPolicy() {
        val state = GameState.newGame(42).copy(
            establishment = EstablishmentState(
                treasury = 25,
                debt = 7,
                heat = 33,
                luxury = 40,
                secrecy = 30,
                pricingPolicy = PricingPolicy.PREMIUM,
                workloadPolicy = WorkloadPolicy.GENTLE,
            ),
            staff = listOf(
                StaffMember(id = "a", name = "Мира", species = "человек", ageYears = 26, loyalty = 12, status = StaffStatus.LEFT),
                StaffMember(id = "b", name = "Ада", species = "эльф", ageYears = 29, loyalty = 65),
            ),
            staffGoals = listOf(
                StaffGoal("goal-b", "b", StaffGoalKind.BUILD_LOYALTY, "укрепить доверие", 75, 65, createdDay = 1, deadlineDay = 8),
            ),
            staffRelations = listOf(StaffRelation("a", "b", affinity = 20, tension = 55, updatedDay = 2)),
        )

        val digest = GameStateDigest.from(state)
        assertTrue(digest.contains("status=LEFT"))
        assertTrue(digest.contains("loyalty=12"))
        assertTrue(digest.contains("Мира"))
        assertTrue(digest.contains("goal=[укрепить доверие 65/75]"))
        assertTrue(digest.contains("a<->b:affinity=20,tension=55"))
        assertTrue(digest.contains("pricing=PREMIUM"))
        assertTrue(digest.contains("workload=GENTLE"))
        assertTrue(digest.contains("luxury=40"))
        assertTrue(digest.contains("secrecy=30"))
    }
}
