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

/** Visual intent is independent from gallery/storage role. */
enum class ImagePromptRole {
    RECRUIT_CARD,
    STAFF_CARD,
    DAY_SCENE,
    HOME_SCENE,
}

enum class EroticTone { MEDIUM, HIGH }

data class StaffFramePrompt(
    val role: GalleryFrameRole,
    val mode: ImagePromptRole,
    val eroticTone: EroticTone,
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

object ScenePromptPlanner {
    private val homeLocations = listOf(
        "the establishment's dim common room before opening, with heavy curtains, oil lamps and a worn velvet settee",
        "a narrow upstairs corridor inside the establishment, warm light spilling from private rooms",
        "a private sitting room inside the establishment with dark drapery, a tall mirror and low amber lamplight",
        "the establishment's reception hall at dusk, with candles, upholstered chairs and a staircase into the private floor",
        "a dressing room inside the establishment with a wooden mirror, folded garments and warm candlelight",
        "a covered balcony attached to the establishment, overlooking the old city while warm light glows behind",
        "the tiled bath antechamber inside the establishment, with steam, towels and dim intimate lighting",
        "the establishment's inner courtyard after rain, with warm windows, hanging fabric and late-evening lanterns",
    )
    private val dayLocations = listOf(
        "the establishment's common room just after closing, with low oil lamps, used tables, curtains and abandoned cups",
        "an upstairs staff corridor after the last visitor has left, with warm light under bedroom doors",
        "a private staff room inside the establishment, with a rumpled chair, small table, coins and late-night lamplight",
        "the establishment's dressing room at the end of the night, with a wooden mirror, loosened ribbons and extinguished candles",
        "a quiet corner of the reception hall after closing, with heavy curtains, worn furniture and scattered cups",
        "the establishment's bath antechamber late at night, with steam, towels, oil lamps and signs of a long working day",
    )
    private val homeActions = listOf(
        "leaning against a doorway while adjusting the edge of her robe",
        "lighting the last candles before evening visitors arrive",
        "standing beside a mirror and smoothing her outfit into place",
        "resting one hand on the banister while watching the room below",
        "sitting on the edge of a settee with relaxed, confident posture",
        "adjusting a stocking or ribbon before the evening begins",
        "carrying a small tray through the room with poised sensual body language",
        "standing near the curtains while the establishment comes alive around her",
    )
    private val dayActions = listOf(
        "counting her personal coins on the edge of a bed after the shift",
        "removing jewelry at a mirror while her work outfit is slightly loosened after a long night",
        "resting alone on a settee after the last visitor has left",
        "putting away a new personal item in the dressing room while unwinding after work",
        "sitting beside a small table with a drink, visibly tired but still composed and sensual",
        "washing up in the bath antechamber while preparing to end the night",
        "loosening her hair and adjusting her clothing after the completed shift",
        "checking the day's notes and earnings in a private room before sleep",
    )
    private val homeFramings = listOf(
        "full-body environmental view with the figure and establishment equally important",
        "three-quarter full-body view from a natural eye-level position",
        "medium-wide vertical composition with the character placed off-center and the room clearly visible",
        "full-body composition with foreground furniture creating depth",
        "wide vertical composition with strong environmental context and an intimate focal figure",
        "three-quarter environmental view with warm foreground light and visible room depth",
    )
    private val dayFramings = listOf(
        "three-quarter full-body environmental view from natural eye level",
        "full-body vertical view from doorway distance with the room clearly visible",
        "medium-wide candid side view with strong establishment context",
        "three-quarter view from an off-center position with furniture and lamplight framing the figure",
        "medium-wide composition with the character on one third and visible aftermath of the shift",
        "wide vertical composition balancing the woman and the lived-in interior",
    )
    private val moods = listOf(
        "tired but sensually self-possessed",
        "quietly pleased and flirtatious",
        "thoughtful, guarded and intimate",
        "mildly amused with confident sensual presence",
        "focused and practical with a teasing undertone",
        "restless after a long shift, still erotically charged",
        "relieved to have a private moment, relaxed and alluring",
        "watchful but relaxed, with mature seductive confidence",
    )

    fun story(staffId: String, day: Int, ordinal: Int): ScenePlan = home(staffId, day, ordinal)

    fun home(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x51A7))
        return ScenePlan(
            location = homeLocations.random(random),
            action = homeActions.random(random),
            framing = homeFramings.random(random),
            mood = moods.random(random),
        )
    }

    fun report(staffId: String, day: Int, ordinal: Int): ScenePlan = day(staffId, day, ordinal)

    fun day(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x7E90))
        return ScenePlan(
            location = dayLocations.random(random),
            action = dayActions.random(random),
            framing = dayFramings.random(random),
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
        promptRole: ImagePromptRole? = null,
        eroticTone: EroticTone? = null,
    ): StaffFramePrompt {
        val resolvedRole = promptRole ?: inferPromptRole(role, scene)
        val resolvedEroticTone = eroticTone ?: defaultEroticTone(resolvedRole)
        val identity = identityBlock(staff, profile)
        val wardrobe = wardrobeBlock(profile, currentInventory, resolvedRole)
        val roleBlock = roleBlock(resolvedRole)
        val eroticBlock = eroticBlock(resolvedEroticTone, resolvedRole)
        val referenceRule = "REFERENCE RULE: if a source image is ever supplied, use it only as an identity hint. Never copy its pose, crop, viewpoint, background, props, panel layout, lighting or composition."
        val style = profile.styleTokens.joinToString(", ").ifBlank { "grounded dark fantasy, painterly realism, mature atmospheric illustration" }
        val sceneBlock = if (scene.isBlank()) "" else "SCENE DIRECTION: $scene"
        val prompt = listOf(
            roleBlock,
            identity,
            wardrobe,
            eroticBlock,
            sceneBlock,
            "STYLE: $style.",
            referenceRule,
            "Natural anatomy, coherent hands, recognizable face, recognizable hair and permanent marks. The image must read as an adult erotic dark-fantasy game illustration, never as a sterile technical reference.",
        ).filter { it.isNotBlank() }.joinToString(" ")
        val negative = "${commonNegative()}, ${roleNegative(resolvedRole)}"
        val (width, height) = dimensions(resolvedRole)
        return StaffFramePrompt(role, resolvedRole, resolvedEroticTone, prompt, negative, width, height)
    }

    private fun inferPromptRole(role: GalleryFrameRole, scene: String): ImagePromptRole = when (role) {
        GalleryFrameRole.PORTRAIT -> if (scene.contains("recruit", ignoreCase = true)) ImagePromptRole.RECRUIT_CARD else ImagePromptRole.STAFF_CARD
        GalleryFrameRole.EVENT -> ImagePromptRole.DAY_SCENE
        GalleryFrameRole.SCENE -> ImagePromptRole.HOME_SCENE
    }

    private fun defaultEroticTone(role: ImagePromptRole): EroticTone = when (role) {
        ImagePromptRole.RECRUIT_CARD -> EroticTone.MEDIUM
        ImagePromptRole.STAFF_CARD -> EroticTone.HIGH
        ImagePromptRole.DAY_SCENE -> EroticTone.HIGH
        ImagePromptRole.HOME_SCENE -> EroticTone.MEDIUM
    }

    private fun identityBlock(staff: StaffMember, profile: VisualIdentityProfile): String {
        val identity = profile.stableIdentityTokens().joinToString(", ")
        return "IDENTITY ANCHOR ONLY: same fictional adult woman, age ${staff.ageYears}; ${staff.species}; $identity. Preserve facial structure, hair, eyes, skin, build and permanent distinguishing marks across images. Identity NEVER includes pose, crop, viewpoint, background, lighting, clothing arrangement or composition."
    }

    private fun wardrobeBlock(
        profile: VisualIdentityProfile,
        currentInventory: List<InventoryItem>,
        role: ImagePromptRole,
    ): String {
        val explicitWardrobe = (profile.wardrobeTokens + currentInventory.flatMap { item ->
            buildList {
                if ("clothing" in item.tags || "jewelry" in item.tags || "visual" in item.tags) add(item.name)
            }
        }).distinct().joinToString(", ")
        val fallback = when (role) {
            ImagePromptRole.RECRUIT_CARD -> "sensual dark-fantasy street or tavern attire fitted to her body, tasteful neckline, visible legs or shoulders where natural"
            ImagePromptRole.STAFF_CARD -> "elegant provocative brothel work attire, lingerie-inspired dark-fantasy styling, fitted fabric, exposed shoulders or thighs where natural"
            ImagePromptRole.DAY_SCENE -> "her believable end-of-shift brothel work attire, slightly loosened or relaxed after work but still coherent and character-specific"
            ImagePromptRole.HOME_SCENE -> "sensual brothel work attire appropriate to the establishment, attractive and lived-in rather than ceremonial"
        }
        val wardrobe = explicitWardrobe.ifBlank { fallback }
        return "WARDROBE STATE: $wardrobe. Clothing and accessories may change with the scene and are not part of facial identity."
    }

    private fun roleBlock(role: ImagePromptRole): String = when (role) {
        ImagePromptRole.RECRUIT_CARD ->
            "IMAGE ROLE — RECRUIT CARD. SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman. FULL-BODY HEAD-TO-TOE introduction portrait: entire body from top of hair to both feet inside frame with comfortable margins. Upright right-side-up pose, readable face, both eyes visible, natural proportions, normal eye-level perspective. This is the player's FIRST EROTIC IMPRESSION of a potential recruit: confident, attractive, memorable and slightly provocative, but still a clean character presentation. Simple dark-fantasy tavern, street-room or brothel-adjacent interior backdrop; no client and no story action."
        ImagePromptRole.STAFF_CARD ->
            "IMAGE ROLE — STAFF CARD. SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman. CANONICAL FULL-BODY HEAD-TO-TOE erotic character portrait: entire body from top of hair to both feet inside frame, readable face, hairstyle, body shape and permanent marks. Upright or gently contrapposto pose, front or soft three-quarter orientation, normal eye-level perspective. This is her definitive in-game erotic dossier image: stronger sensuality than recruitment, elegant seductive posture and confident adult presence, but no client and no narrative action."
        ImagePromptRole.DAY_SCENE ->
            "IMAGE ROLE — DAY SCENE. SINGLE CONTINUOUS IMAGE. Exactly one fictional adult woman in a NEW narrative end-of-day scene INSIDE THE DARK-FANTASY BROTHEL/ESTABLISHMENT. This is NOT a portrait. The room and signs of the completed workday must occupy a substantial part of the frame: lamps, curtains, worn furniture, mirrors, private rooms, clothing, cups, coins, bedding or service areas as contextually appropriate. The scene must visually communicate how this specific day felt for her. Erotic atmosphere is mandatory and integrated into posture, styling, intimate lighting and the lived-in brothel environment."
        ImagePromptRole.HOME_SCENE ->
            "IMAGE ROLE — HOME SCENE. SINGLE CONTINUOUS IMAGE. A living atmospheric view of the dark-fantasy brothel/establishment, with one fictional adult staff woman as the visual focus when a character is present. Environment comes first: rooms, stairs, curtains, low lamps, reception spaces, mirrors, private doors and signs of an operating establishment. The scene must immediately feel sensual, intimate and erotically charged, never like a generic fantasy tavern or neutral interior."
    }

    private fun eroticBlock(tone: EroticTone, role: ImagePromptRole): String {
        val core = "MANDATORY EROTIC CORE: every generated image must have unmistakable adult erotic energy. Use mature sensual body language, confident or teasing gaze when the face is visible, attractive posture, intimate warm lighting, tactile fabrics and a seductive brothel-world mood. Erotic emphasis must belong to the WHOLE character and scene, never isolate feet, legs, breasts or any other body part as the sole subject. Keep anatomy natural and the person recognizably the same adult woman."
        val intensity = when (tone) {
            EroticTone.MEDIUM -> "EROTIC INTENSITY — MEDIUM: clearly sensual and flirtatious, body-conscious or revealing attire, suggestive posture and intimate atmosphere, while keeping the composition readable and role-appropriate."
            EroticTone.HIGH -> "EROTIC INTENSITY — HIGH: strongly seductive adult presentation, provocative but coherent pose, lingerie-inspired or partially loosened work attire where scene-appropriate, exposed shoulders, back or thighs where natural, stronger intimate tension and boudoir-like warmth. Keep it non-graphic: no explicit sex act and no explicit genital focus."
        }
        val roleGuard = when (role) {
            ImagePromptRole.RECRUIT_CARD -> "Eroticism should sell first impression and personality, not overpower face or full-body readability."
            ImagePromptRole.STAFF_CARD -> "Eroticism should make the canonical character portrait alluring and memorable without turning it into an action scene."
            ImagePromptRole.DAY_SCENE -> "Eroticism should feel like the sensual afterglow, fatigue, confidence or intimacy of a real completed shift in the establishment."
            ImagePromptRole.HOME_SCENE -> "Eroticism should emerge from both the focal woman and the atmosphere of the establishment itself."
        }
        return "$core $intensity $roleGuard"
    }

    private fun commonNegative(): String =
        "child, teen, underage, young-looking, ambiguous age, collage, grid, split screen, diptych, triptych, contact sheet, comic panels, storyboard, multiple panels, repeated frame, duplicated composition, duplicate person, cloned face, extra limbs, extra fingers, fused body, deformed hands, missing hands, malformed face, inconsistent hair, inconsistent eye color, photo camera, photographic camera, camera device, camera equipment, camera lens, tripod, photographer, CCTV camera, surveillance camera, action camera, feet close-up, foot close-up, soles close-up, feet only, legs only, body-part fetish framing, disembodied limbs, giant feet, tiny head, extreme foreshortening, explicit intercourse, explicit sex act, genital close-up"

    private fun roleNegative(role: ImagePromptRole): String = when (role) {
        ImagePromptRole.RECRUIT_CARD ->
            "busy action scene, client, man, male, multiple people, extreme viewpoint, floor-level viewpoint, cropped head, cropped face, cropped feet, feet outside frame, headless, faceless, upside-down person, inverted body, back-facing portrait, plain passport photo, sterile character sheet, armor covering the whole silhouette, bulky shapeless clothing"
        ImagePromptRole.STAFF_CARD ->
            "busy action scene, client, man, male, multiple people, extreme viewpoint, floor-level viewpoint, cropped head, cropped face, cropped feet, feet outside frame, headless, faceless, upside-down person, inverted body, back-facing portrait, sterile neutral passport photo, clinical reference sheet, bulky shapeless clothing"
        ImagePromptRole.DAY_SCENE ->
            "studio portrait, casting portrait, character sheet, neutral standing pose, plain backdrop, empty white room, abstract background, centered passport composition, product photography, isolated object, generic fantasy tavern, unrelated street scene, celebratory poster, empty room with no lived-in details"
        ImagePromptRole.HOME_SCENE ->
            "studio portrait, casting portrait, character sheet, plain backdrop, empty white room, abstract background, generic fantasy tavern, generic medieval inn, product photography, isolated object, sterile architecture render, empty environment with no sensual atmosphere"
    }

    private fun dimensions(role: ImagePromptRole): Pair<Int, Int> = when (role) {
        ImagePromptRole.RECRUIT_CARD, ImagePromptRole.STAFF_CARD -> 768 to 1152
        ImagePromptRole.DAY_SCENE, ImagePromptRole.HOME_SCENE -> 768 to 1024
    }
}
