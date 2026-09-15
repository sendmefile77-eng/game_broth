package com.sendmefile77.gamebroth.aiimage

import com.sendmefile77.gamebroth.model.GalleryFrameRole
import com.sendmefile77.gamebroth.model.InventoryItem
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.VisualIdentityProfile
import kotlin.random.Random

data class ImageAiStatus(
    val available: Boolean,
    val detail: String? = null,
)

data class ImageGenerationRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val width: Int = 768,
    val height: Int = 1024,
    val steps: Int = 12,
    val cfgScale: Double = 4.0,
    val seed: Long,
    val cacheKey: String,
    val referenceImageBytes: ByteArray? = null,
    val referenceStrength: Double = 0.62,
)

data class ImageGenerationResult(
    val bytes: ByteArray,
    val seed: Long?,
    val width: Int,
    val height: Int,
    val generationTimeMs: Long?,
)

interface ImageGenerator {
    suspend fun status(force: Boolean = false): ImageAiStatus
    suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult?
}

enum class FramePromptMode { PORTRAIT, STORY, REPORT }

data class StaffFramePrompt(
    val role: GalleryFrameRole,
    val mode: FramePromptMode,
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
)

data class ScenePlan(
    val location: String,
    val action: String,
    val camera: String,
    val mood: String,
) {
    fun asPrompt(): String = "Location: $location. Action: $action. Camera: $camera. Mood: $mood."
}

/**
 * Produces varied but deterministic scene direction. Identity never lives here:
 * this class owns only location/action/camera/mood, so scene variation cannot
 * silently rewrite a character's canonical appearance.
 */
object ScenePromptPlanner {
    private val locations = listOf(
        "a narrow upstairs corridor lit by two oil lamps",
        "a private sitting room with worn plaster and a tall window",
        "a quiet inner courtyard after rain",
        "a service kitchen with copper pans and low firelight",
        "a covered balcony overlooking the old city roofs",
        "a small dressing room with a wooden mirror and folded clothes",
        "a tiled bath antechamber with steam in the background",
        "the establishment's dim common room before opening",
    )
    private val actions = listOf(
        "counting her personal coins at a small table",
        "adjusting a newly bought accessory before a mirror",
        "reading a short handwritten note",
        "resting after work with a cup in one hand",
        "tying back her hair before the next shift",
        "putting away clothes after returning to her room",
        "standing by an open window and watching the street below",
        "talking quietly while leaning against a wooden table",
    )
    private val cameras = listOf(
        "three-quarter medium shot from slightly below eye level",
        "full-body environmental shot from doorway distance",
        "waist-up candid side angle with visible background",
        "three-quarter full-body shot from an off-center angle",
        "medium-wide shot with the character placed on the left third",
        "full-body shot with foreground objects creating depth",
        "close medium shot from a gentle high angle",
        "wide vertical composition with strong environmental context",
    )
    private val moods = listOf(
        "tired but calm",
        "quietly pleased",
        "thoughtful and guarded",
        "mildly amused",
        "focused and practical",
        "restless after a long shift",
        "relieved to have a private moment",
        "watchful but relaxed",
    )

    private val reportActions = listOf(
        "reviewing the day's earnings and purchases",
        "sitting alone after the last visitor has left",
        "putting away a new personal item bought today",
        "resting while the establishment is being closed for the night",
        "checking a small notebook before going to sleep",
        "washing up and preparing to end the day",
    )

    fun story(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x51A7))
        return ScenePlan(
            location = locations.random(random),
            action = actions.random(random),
            camera = cameras.random(random),
            mood = moods.random(random),
        )
    }

    fun report(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x7E90))
        return ScenePlan(
            location = locations.random(random),
            action = reportActions.random(random),
            camera = cameras.random(random),
            mood = moods.random(random),
        )
    }

    private fun seed(staffId: String, day: Int, ordinal: Int, salt: Int): Int =
        staffId.hashCode() xor (day * 73_856_093) xor (ordinal * 19_349_663) xor salt
}

object VisualPromptBuilder {
    fun build(
        staff: StaffMember,
        profile: VisualIdentityProfile,
        role: GalleryFrameRole,
        scene: String,
        currentInventory: List<InventoryItem> = staff.inventory,
        mode: FramePromptMode = when (role) {
            GalleryFrameRole.PORTRAIT -> FramePromptMode.PORTRAIT
            GalleryFrameRole.SCENE -> FramePromptMode.STORY
            GalleryFrameRole.EVENT -> FramePromptMode.REPORT
        },
    ): StaffFramePrompt {
        val identity = profile.stableIdentityTokens().joinToString(", ")
        val wardrobe = (profile.wardrobeTokens + currentInventory.flatMap { item ->
            buildList {
                if ("clothing" in item.tags || "jewelry" in item.tags || "visual" in item.tags) add(item.name)
            }
        }).distinct().joinToString(", ").ifBlank { "simple dark-fantasy work clothes" }
        val style = profile.styleTokens.joinToString(", ").ifBlank { "grounded dark fantasy, painterly realism" }

        val identityBlock = "IDENTITY ANCHOR ONLY: same fictional adult woman; ${staff.species}; $identity. Preserve these facial, hair, eye, skin, body-plan and distinguishing-mark traits across images. Do NOT treat identity as pose, framing, background, lighting or composition."
        val wardrobeBlock = "Current clothing and accessories: $wardrobe. Wardrobe is scene state, not part of facial identity."
        val roleText = when (mode) {
            FramePromptMode.PORTRAIT -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman. Full-height casting portrait, head to feet, face clearly visible, relaxed neutral standing pose, uncomplicated background, one camera. This is a CHARACTER REFERENCE PORTRAIT, not a story scene."
            FramePromptMode.STORY -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman in a NEW environmental STORY SCENE. This must NOT look like a casting portrait or character sheet. Use a new pose, new camera placement, new framing, visible environment and natural action."
            FramePromptMode.REPORT -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman in a NEW end-of-day narrative scene. Show aftermath and environment; avoid neutral portrait posing and avoid reusing the canonical composition."
        }
        val referenceRule = "If any source/reference image is ever supplied, use it only as an identity hint. Never copy its pose, crop, camera angle, background, props, panel layout, lighting or composition."
        val prompt = "$roleText $identityBlock $wardrobeBlock Scene direction: $scene Style: $style. $referenceRule Natural anatomy, coherent hands, recognizable face, recognizable hair and permanent marks."

        val commonNegative = "child, teen, underage, young-looking, collage, grid, split screen, diptych, triptych, contact sheet, comic panels, storyboard, multiple panels, repeated frame, duplicated composition, duplicate person, cloned face, extra limbs, extra fingers, fused body, deformed hands, missing hands, malformed face, inconsistent hair, inconsistent eye color"
        val modeNegative = when (mode) {
            FramePromptMode.PORTRAIT -> "busy action scene, crowd, multiple people, two people, man, male, extreme camera angle, cropped feet, environmental storytelling overload"
            FramePromptMode.STORY -> "studio portrait, casting portrait, character sheet, neutral standing pose, plain backdrop, centered passport composition, same pose as reference, same background as reference"
            FramePromptMode.REPORT -> "studio portrait, casting portrait, character sheet, neutral standing pose, plain backdrop, centered passport composition, celebratory poster, same pose as reference, same background as reference"
        }
        val negative = "$commonNegative, $modeNegative"
        val (w, h) = when (role) {
            GalleryFrameRole.EVENT -> 1152 to 768
            else -> 768 to 1024
        }
        return StaffFramePrompt(role, mode, prompt, negative, w, h)
    }
}
