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
    val framing: String,
    val mood: String,
) {
    fun asPrompt(): String = "Location: $location. Action: $action. Framing and viewpoint: $framing. Mood: $mood."
}

/** Identity lives in VisualIdentityProfile; this planner owns only scene variation. */
object ScenePromptPlanner {
    private val locations = listOf(
        "a narrow upstairs staff corridor inside the establishment, lit by two oil lamps",
        "a private sitting room inside the establishment with worn plaster, curtains and a tall window",
        "a quiet inner courtyard of the establishment after rain, with warm windows behind",
        "the establishment's service kitchen with copper pans and low firelight",
        "a covered balcony attached to the establishment, overlooking the old city roofs",
        "a small dressing room inside the establishment with a wooden mirror and folded clothes",
        "a tiled bath antechamber inside the establishment with steam and folded towels in the background",
        "the establishment's dim common room before opening, with tables, curtains and oil lamps",
    )
    private val reportLocations = listOf(
        "the establishment's common room just after closing, with low oil lamps, used tables, curtains and abandoned cups",
        "an upstairs staff corridor of the establishment after the last guest has left, with warm light under bedroom doors",
        "a private staff room inside the establishment, with a rumpled chair, small table, coins and late-night lamplight",
        "the establishment's dressing room at the end of the night, with a wooden mirror, folded clothes and extinguished candles",
        "a quiet corner of the establishment's reception hall after closing, with heavy curtains, worn furniture and scattered cups",
        "the establishment's bath antechamber late at night, with steam, towels, oil lamps and signs of a long working day",
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
    private val framings = listOf(
        "three-quarter medium view from slightly below eye level",
        "full-body environmental view from doorway distance",
        "waist-up candid side view with the room clearly visible",
        "three-quarter full-body view from an off-center position",
        "medium-wide composition with the character placed on the left third",
        "full-body composition with foreground furniture creating depth",
        "close medium view from a gentle high viewpoint",
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
        "reviewing the day's earnings and purchases at a small table",
        "sitting alone after the last visitor has left while the establishment is being closed",
        "putting away a new personal item bought today in the dressing room",
        "resting while lamps are being extinguished for the night",
        "checking a small notebook before going to sleep",
        "washing up and preparing to end the day",
    )

    fun story(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x51A7))
        return ScenePlan(locations.random(random), actions.random(random), framings.random(random), moods.random(random))
    }

    fun report(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x7E90))
        return ScenePlan(reportLocations.random(random), reportActions.random(random), framings.random(random), moods.random(random))
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

        val identityBlock = "IDENTITY ANCHOR ONLY: same fictional adult woman; ${staff.species}; $identity. Preserve facial structure, hair, eyes, skin, build and permanent distinguishing marks across images. Identity does not include pose, crop, viewpoint, background, lighting or composition."
        val wardrobeBlock = "Current clothing and accessories: $wardrobe. Wardrobe is scene state, not facial identity."
        val roleText = when (mode) {
            FramePromptMode.PORTRAIT -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman, upright and right-side-up. THREE-QUARTER CHARACTER REFERENCE PORTRAIT from the top of the head to approximately the knees. Her complete head and face MUST be visible in the upper third of the frame; both eyes, hairstyle, jawline, shoulders, torso and arms must be clearly readable. Natural relaxed standing pose, normal human-scale perspective, simple unobtrusive background. The FACE is the primary visual anchor. Do not emphasize legs or feet. This is an identity portrait, not a body-part study and not an action scene."
            FramePromptMode.STORY -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman in a NEW environmental STORY SCENE. This must NOT look like a casting portrait or character sheet. Use a new pose, new viewpoint, new framing, visible environment and natural action."
            FramePromptMode.REPORT -> "SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman in a NEW end-of-day narrative scene INSIDE A DARK-FANTASY BROTHEL/ESTABLISHMENT. The establishment interior must be clearly visible and important to the image: warm oil lamps, curtains, worn furniture, tables, doors, personal rooms or service areas, and believable signs of the working day. Show aftermath and atmosphere rather than a neutral portrait. Do not turn the scene into an object study."
        }
        val referenceRule = "If any source/reference image is ever supplied, use it only as an identity hint. Never copy its pose, crop, viewpoint, background, props, panel layout, lighting or composition."
        val prompt = "$roleText $identityBlock $wardrobeBlock Scene direction: $scene Style: $style. $referenceRule Natural anatomy, coherent hands, recognizable face, recognizable hair and permanent marks."

        val commonNegative = "child, teen, underage, young-looking, collage, grid, split screen, diptych, triptych, contact sheet, comic panels, storyboard, multiple panels, repeated frame, duplicated composition, duplicate person, cloned face, extra limbs, extra fingers, fused body, deformed hands, missing hands, malformed face, inconsistent hair, inconsistent eye color, photo camera, photographic camera, camera device, camera equipment, camera lens, tripod, photographer, CCTV camera, surveillance camera, action camera"
        val modeNegative = when (mode) {
            FramePromptMode.PORTRAIT -> "busy action scene, crowd, multiple people, two people, man, male, extreme viewpoint, extreme perspective, extreme foreshortening, feet close-up, foot close-up, soles, bare soles, feet only, legs only, legs close-up, body-part crop, disembodied limbs, headless, faceless, face outside frame, cropped head, cropped face, upside-down person, inverted body, back-facing portrait, low-angle foot focus, floor-level viewpoint, giant feet, tiny head, cropped torso"
            FramePromptMode.STORY -> "studio portrait, casting portrait, character sheet, neutral standing pose, plain backdrop, centered passport composition, same pose as reference, same background as reference, product photography, isolated object"
            FramePromptMode.REPORT -> "studio portrait, casting portrait, character sheet, neutral standing pose, plain backdrop, centered passport composition, celebratory poster, same pose as reference, same background as reference, product photography, isolated object, empty white room, abstract background"
        }
        return StaffFramePrompt(role, mode, prompt, "$commonNegative, $modeNegative", 768, 1024)
    }
}
