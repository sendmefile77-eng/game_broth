package com.sendmefile77.gamebroth.aiimage

import com.sendmefile77.gamebroth.model.GalleryFrameRole
import com.sendmefile77.gamebroth.model.InventoryItem
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.VisualIdentityProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPromptBuilderTest {
    @Test fun stableIdentityAndCurrentPurchasesAreIncluded() {
        val staff = StaffMember(
            id = "w1", name = "Нира", species = "речная амфибия", ageYears = 26,
            inventory = listOf(InventoryItem("earrings", "медные серьги", tags = setOf("jewelry"))),
        )
        val profile = VisualIdentityProfile(
            staffId = "w1", ageBand = "adult 26 years old", build = "tall lean build",
            skinTone = "sea-green skin", hair = "long black wavy hair", eyes = "amber eyes",
            face = "sharp cheekbones", distinctiveMarks = listOf("scar above left eyebrow"),
            speciesTokens = listOf("subtle neck gills"),
        )
        val prompt = VisualPromptBuilder.build(staff, profile, GalleryFrameRole.PORTRAIT, "standing by a lamp").prompt
        assertTrue(prompt.contains("long black wavy hair"))
        assertTrue(prompt.contains("amber eyes"))
        assertTrue(prompt.contains("scar above left eyebrow"))
        assertTrue(prompt.contains("медные серьги"))
    }
}
