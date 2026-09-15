package com.sendmefile77.gamebroth.aiimage

import com.sendmefile77.gamebroth.model.GalleryFrameRole
import com.sendmefile77.gamebroth.model.InventoryItem
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.VisualIdentityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPromptBuilderTest {
    private val staff = StaffMember(
        id = "w1", name = "Нира", species = "речная амфибия", ageYears = 26,
        inventory = listOf(InventoryItem("earrings", "медные серьги", tags = setOf("jewelry"))),
    )
    private val profile = VisualIdentityProfile(
        staffId = "w1", ageBand = "adult 26 years old", build = "tall lean build",
        skinTone = "sea-green skin", hair = "long black wavy hair", eyes = "amber eyes",
        face = "sharp cheekbones", distinctiveMarks = listOf("scar above left eyebrow"),
        speciesTokens = listOf("subtle neck gills"),
    )

    @Test fun stableIdentityAndCurrentPurchasesAreIncluded() {
        val prompt = VisualPromptBuilder.build(staff, profile, GalleryFrameRole.PORTRAIT, "standing by a lamp").prompt
        assertTrue(prompt.contains("long black wavy hair"))
        assertTrue(prompt.contains("amber eyes"))
        assertTrue(prompt.contains("scar above left eyebrow"))
        assertTrue(prompt.contains("медные серьги"))
    }

    @Test fun storyPromptSeparatesIdentityFromComposition() {
        val built = VisualPromptBuilder.build(
            staff,
            profile,
            GalleryFrameRole.SCENE,
            "Location: balcony. Action: reading. Camera: side angle. Mood: calm.",
        )
        assertEquals(FramePromptMode.STORY, built.mode)
        assertTrue(built.prompt.contains("IDENTITY ANCHOR ONLY"))
        assertTrue(built.prompt.contains("new pose", ignoreCase = true))
        assertTrue(built.negativePrompt.contains("studio portrait"))
        assertTrue(built.negativePrompt.contains("same pose as reference"))
    }

    @Test fun scenePlannerVariesByOrdinalButIsRepeatable() {
        val a = ScenePromptPlanner.story("w1", 7, 2)
        val repeat = ScenePromptPlanner.story("w1", 7, 2)
        val b = ScenePromptPlanner.story("w1", 7, 3)
        assertEquals(a, repeat)
        assertNotEquals(a, b)
    }
}
