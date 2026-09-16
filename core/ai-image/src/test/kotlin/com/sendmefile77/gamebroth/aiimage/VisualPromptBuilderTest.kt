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
        val prompt = VisualPromptBuilder.build(
            staff, profile, GalleryFrameRole.PORTRAIT, "staff identity portrait",
            promptRole = ImagePromptRole.STAFF_CARD,
        ).prompt
        assertTrue(prompt.contains("long black wavy hair"))
        assertTrue(prompt.contains("amber eyes"))
        assertTrue(prompt.contains("scar above left eyebrow"))
        assertTrue(prompt.contains("медные серьги"))
    }

    @Test fun recruitAndStaffCardsAreSeparateModes() {
        val recruit = VisualPromptBuilder.build(
            staff, profile, GalleryFrameRole.PORTRAIT,
            "Recruitment identity portrait. Full body from head to both feet.",
        )
        val staffCard = VisualPromptBuilder.build(
            staff, profile, GalleryFrameRole.PORTRAIT,
            "full-body head-to-toe identity portrait",
        )
        assertEquals(ImagePromptRole.RECRUIT_CARD, recruit.mode)
        assertEquals(ImagePromptRole.STAFF_CARD, staffCard.mode)
        assertEquals(EroticTone.MEDIUM, recruit.eroticTone)
        assertEquals(EroticTone.HIGH, staffCard.eroticTone)
        assertTrue(recruit.prompt.contains("FIRST EROTIC IMPRESSION"))
        assertTrue(staffCard.prompt.contains("definitive in-game erotic dossier image"))
        assertNotEquals(recruit.prompt, staffCard.prompt)
    }

    @Test fun everyVisualRoleContainsMandatoryEroticCore() {
        ImagePromptRole.entries.forEach { role ->
            val storageRole = when (role) {
                ImagePromptRole.RECRUIT_CARD, ImagePromptRole.STAFF_CARD -> GalleryFrameRole.PORTRAIT
                ImagePromptRole.DAY_SCENE -> GalleryFrameRole.EVENT
                ImagePromptRole.HOME_SCENE -> GalleryFrameRole.SCENE
            }
            val built = VisualPromptBuilder.build(
                staff, profile, storageRole, "test scene", promptRole = role,
            )
            assertEquals(role, built.mode)
            assertTrue(built.prompt.contains("MANDATORY EROTIC CORE"))
            assertTrue(built.prompt.contains("adult erotic energy"))
            assertTrue(built.prompt.contains("never isolate feet"))
            assertTrue(built.negativePrompt.contains("underage"))
            assertTrue(built.negativePrompt.contains("body-part fetish framing"))
        }
    }

    @Test fun staffCardRequiresTrueHeadToToeCompositionWithoutFootFocus() {
        val built = VisualPromptBuilder.build(
            staff, profile, GalleryFrameRole.PORTRAIT, "canonical staff portrait",
            promptRole = ImagePromptRole.STAFF_CARD,
        )
        assertEquals(ImagePromptRole.STAFF_CARD, built.mode)
        assertEquals(768, built.width)
        assertEquals(1152, built.height)
        assertTrue(built.prompt.contains("FULL-BODY HEAD-TO-TOE"))
        assertTrue(built.prompt.contains("both feet inside frame"))
        assertTrue(built.negativePrompt.contains("feet close-up"))
        assertTrue(built.negativePrompt.contains("headless"))
        assertTrue(built.negativePrompt.contains("cropped feet"))
    }

    @Test fun daySceneRequiresBrothelContextAndHighEroticTone() {
        val built = VisualPromptBuilder.build(
            staff,
            profile,
            GalleryFrameRole.EVENT,
            ScenePromptPlanner.day("w1", 7, 2).asPrompt(),
        )
        assertEquals(ImagePromptRole.DAY_SCENE, built.mode)
        assertEquals(EroticTone.HIGH, built.eroticTone)
        assertEquals(768, built.width)
        assertEquals(1024, built.height)
        assertTrue(built.prompt.contains("INSIDE THE DARK-FANTASY BROTHEL/ESTABLISHMENT"))
        assertTrue(built.prompt.contains("completed workday"))
        assertTrue(built.prompt.contains("Erotic atmosphere is mandatory"))
        assertTrue(built.negativePrompt.contains("generic fantasy tavern"))
        assertTrue(built.negativePrompt.contains("photo camera"))
    }

    @Test fun homeSceneIsEnvironmentFirstButStillErotic() {
        val built = VisualPromptBuilder.build(
            staff,
            profile,
            GalleryFrameRole.SCENE,
            ScenePromptPlanner.home("w1", 7, 2).asPrompt(),
        )
        assertEquals(ImagePromptRole.HOME_SCENE, built.mode)
        assertEquals(EroticTone.MEDIUM, built.eroticTone)
        assertTrue(built.prompt.contains("Environment comes first"))
        assertTrue(built.prompt.contains("sensual, intimate and erotically charged"))
    }

    @Test fun scenePlannerVariesByOrdinalButIsRepeatable() {
        val a = ScenePromptPlanner.day("w1", 7, 2)
        val repeat = ScenePromptPlanner.day("w1", 7, 2)
        val b = ScenePromptPlanner.day("w1", 7, 3)
        assertEquals(a, repeat)
        assertNotEquals(a, b)
        assertTrue(a.asPrompt().contains("Framing and viewpoint"))
    }
}
