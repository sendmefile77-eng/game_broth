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
    val referenceStrength: Double = 0.60,
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
    /**
     * Local Dream / Illustrious works better with concise comma-separated visual phrases
     * than with prose instructions. Keep labels out of the actual model prompt.
     */
    fun asPrompt(): String = listOf(location, action, framing, mood).joinToString(", ")
}

object ScenePromptPlanner {
    private val homeLocations = listOf(
        "dark fantasy brothel common room, heavy curtains, warm oil lamps, worn velvet settee",
        "upstairs brothel corridor, warm light under private room doors, intimate atmosphere",
        "private sitting room, dark drapery, tall mirror, low amber lamplight",
        "brothel reception hall at dusk, candles, upholstered chairs, staircase to private rooms",
        "brothel dressing room, wooden mirror, folded garments, warm candlelight",
        "covered balcony attached to the brothel, old city roofs, warm room light behind",
        "tiled bath antechamber, steam, folded towels, dim intimate lighting",
        "inner courtyard after rain, warm windows, hanging fabric, late-evening lanterns",
    )
    private val dayLocations = listOf(
        "brothel common room after closing, low oil lamps, used tables, curtains, abandoned cups",
        "upstairs brothel corridor after closing, warm light under private room doors",
        "private staff room, rumpled chair, small table, coins, late-night lamplight",
        "brothel dressing room after closing, wooden mirror, loosened ribbons, extinguished candles",
        "quiet reception hall after closing, heavy curtains, worn furniture, scattered cups",
        "bath antechamber late at night, steam, towels, oil lamps, signs of a long working day",
    )
    private val homeActions = listOf(
        "adjusting the edge of her robe in a doorway",
        "lighting candles before evening visitors arrive",
        "smoothing her outfit in front of a mirror",
        "resting one hand on the banister, watching the room below",
        "sitting on the edge of a settee, relaxed confident posture",
        "adjusting a stocking or ribbon before the evening begins",
        "carrying a small tray, poised sensual body language",
        "standing near heavy curtains while the establishment comes alive",
    )
    private val dayActions = listOf(
        "counting personal coins on the edge of a bed after work",
        "removing jewelry at a mirror, work outfit slightly loosened after a long shift",
        "resting alone on a settee after the last visitor has left",
        "putting away a new personal item in the dressing room after work",
        "sitting beside a small table with a drink, visibly tired but composed",
        "washing up in the bath antechamber at the end of the night",
        "loosening her hair and adjusting clothing after the completed shift",
        "checking the day's notes and earnings in a private room before sleep",
    )
    private val homeFramings = listOf(
        "full body environmental composition, natural eye level",
        "three-quarter full body composition, natural eye level",
        "medium-wide vertical composition, character off-center, room clearly visible",
        "full body composition, foreground furniture, strong depth",
        "wide vertical composition, intimate focal figure, strong environmental context",
        "three-quarter environmental composition, warm foreground light, visible room depth",
    )
    private val dayFramings = listOf(
        "three-quarter full body environmental composition, natural eye level",
        "full body vertical composition from doorway distance, room clearly visible",
        "medium-wide candid side composition, strong brothel context",
        "three-quarter composition, off-center figure, furniture and lamplight framing",
        "medium-wide composition, character on one third, visible aftermath of shift",
        "wide vertical composition, balanced woman and lived-in interior",
    )
    private val moods = listOf(
        "tired but sensually self-possessed",
        "quietly pleased, flirtatious",
        "thoughtful, guarded, intimate",
        "mildly amused, confident sensual presence",
        "focused, practical, teasing undertone",
        "restless after a long shift, sensual tension",
        "relieved to have a private moment, relaxed and alluring",
        "watchful but relaxed, mature seductive confidence",
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
        val tags = linkedSetOf<String>()

        tags += baseQualityTags()
        tags += identityTags(staff, profile)
        tags += roleTags(resolvedRole)
        tags += wardrobeTags(profile, currentInventory, resolvedRole)
        tags += eroticTags(resolvedEroticTone, resolvedRole)
        tags += sceneTags(resolvedRole, scene)
        tags += styleTags(profile)

        val prompt = tags.filter { it.isNotBlank() }.joinToString(", ")
        val negative = (commonNegativeTags() + roleNegativeTags(resolvedRole))
            .distinct()
            .joinToString(", ")
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

    private fun baseQualityTags(): List<String> = listOf(
        "1girl",
        "solo",
        "mature adult woman",
        "adult female",
        "clearly adult facial features",
        "adult body proportions",
        "detailed face",
        "detailed eyes",
        "natural anatomy",
        "coherent hands",
        "high quality illustration",
    )

    private fun identityTags(staff: StaffMember, profile: VisualIdentityProfile): List<String> = buildList {
        add("age ${staff.ageYears}")
        add(staff.species)
        add(profile.build)
        add(profile.skinTone)
        add(profile.hair)
        add(profile.eyes)
        add(profile.face)
        addAll(profile.speciesTokens)
        addAll(profile.distinctiveMarks)
        add(profile.bodyPlan)
        add("consistent character identity")
        add("recognizable face")
        add("recognizable hairstyle")
    }

    private fun wardrobeTags(
        profile: VisualIdentityProfile,
        currentInventory: List<InventoryItem>,
        role: ImagePromptRole,
    ): List<String> {
        val explicit = (profile.wardrobeTokens + currentInventory.flatMap { item ->
            buildList {
                if ("clothing" in item.tags || "jewelry" in item.tags || "visual" in item.tags) add(item.name)
            }
        }).distinct()
        if (explicit.isNotEmpty()) return explicit
        return when (role) {
            ImagePromptRole.RECRUIT_CARD -> listOf(
                "fitted dark fantasy outfit",
                "revealing but non-explicit clothing",
                "bare shoulders",
                "visible thighs",
                "body-conscious silhouette",
            )
            ImagePromptRole.STAFF_CARD -> listOf(
                "elegant provocative brothel outfit",
                "lingerie-inspired dark fantasy styling",
                "fitted bodice",
                "stockings",
                "bare shoulders",
                "visible thighs",
            )
            ImagePromptRole.DAY_SCENE -> listOf(
                "brothel work outfit",
                "slightly loosened clothing after work",
                "lived-in wardrobe",
            )
            ImagePromptRole.HOME_SCENE -> listOf(
                "sensual brothel work outfit",
                "fitted dark fantasy clothing",
            )
        }
    }

    private fun roleTags(role: ImagePromptRole): List<String> = when (role) {
        ImagePromptRole.RECRUIT_CARD -> listOf(
            "full body",
            "head to toe",
            "entire body visible",
            "both feet visible",
            "standing",
            "upright pose",
            "front view or gentle three-quarter view",
            "looking at viewer",
            "normal eye-level perspective",
            "simple dark fantasy brothel-adjacent interior",
            "clean background",
            "first impression",
        )
        ImagePromptRole.STAFF_CARD -> listOf(
            "full body",
            "head to toe",
            "entire body visible",
            "both feet visible",
            "standing",
            "elegant contrapposto",
            "front view or gentle three-quarter view",
            "looking at viewer",
            "normal eye-level perspective",
            "simple intimate brothel interior",
            "canonical appearance",
        )
        ImagePromptRole.DAY_SCENE -> listOf(
            "dark fantasy brothel interior",
            "end of day",
            "after work",
            "lived-in room",
            "warm oil lamp light",
            "curtains",
            "worn furniture",
            "mirror",
            "private room atmosphere",
            "visual aftermath of completed shift",
            "environment clearly visible",
        )
        ImagePromptRole.HOME_SCENE -> listOf(
            "dark fantasy brothel interior",
            "operating establishment",
            "warm intimate lighting",
            "curtains",
            "mirrors",
            "private doors",
            "reception or common room",
            "environment clearly visible",
            "lived-in sensual atmosphere",
        )
    }

    private fun eroticTags(tone: EroticTone, role: ImagePromptRole): List<String> {
        val common = mutableListOf(
            "sensual",
            "seductive",
            "alluring",
            "mature erotic atmosphere",
            "sensual body language",
            "confident adult presence",
            "intimate warm lighting",
        )
        when (tone) {
            EroticTone.MEDIUM -> common += listOf(
                "flirtatious expression",
                "suggestive pose",
                "body-conscious styling",
            )
            EroticTone.HIGH -> common += listOf(
                "strongly seductive pose",
                "provocative styling",
                "boudoir mood",
                "intimate tension",
            )
        }
        when (role) {
            ImagePromptRole.RECRUIT_CARD -> common += "playful confident gaze"
            ImagePromptRole.STAFF_CARD -> common += "memorable seductive gaze"
            ImagePromptRole.DAY_SCENE -> common += listOf("sensual post-shift mood", "erotic brothel ambience")
            ImagePromptRole.HOME_SCENE -> common += listOf("erotic establishment ambience", "inviting intimate mood")
        }
        return common
    }

    private fun styleTags(profile: VisualIdentityProfile): List<String> = buildList {
        add("dark fantasy")
        add("mature character design")
        add("semi-realistic anime illustration")
        add("adult proportions")
        addAll(profile.styleTokens.filterNot { it.contains("consistent character identity", ignoreCase = true) })
    }

    private fun sceneTags(role: ImagePromptRole, scene: String): List<String> {
        if (role == ImagePromptRole.RECRUIT_CARD || role == ImagePromptRole.STAFF_CARD || scene.isBlank()) return emptyList()
        val result = mutableListOf<String>()

        // ScenePlan is already compact comma-separated visual language.
        val prefix = scene.substringBefore("THIS IS THE VISUAL SUMMARY", scene)
        prefix.split(',')
            .map { it.trim().trimEnd('.') }
            .filter { it.length in 3..140 }
            .take(14)
            .forEach(result::add)

        if (role == ImagePromptRole.DAY_SCENE) {
            val lower = scene.lowercase()
            when {
                "difficult incident" in lower || "incident fact:" in lower -> result += listOf("tense aftermath", "recovering after difficult shift")
                "visibly successful" in lower || "successful, relieved" in lower -> result += listOf("satisfied after successful shift", "quiet confidence")
                "heavy physical fatigue" in lower -> result += listOf("visibly tired", "relaxed post-shift posture")
                "visible tension" in lower -> result += listOf("tense expression", "private decompression")
                else -> result += "ordinary end-of-shift intimacy"
            }
            Regex("Purchase fact: ([^,]+),", RegexOption.IGNORE_CASE).find(scene)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
                result += "new personal item: $it"
            }
            if ("coins" in lower || "business earned" in lower) result += "coins on table"
            if ("cup" in lower || "drink" in lower) result += "drink nearby"
        }
        return result
    }

    private fun commonNegativeTags(): List<String> = listOf(
        "child",
        "teen",
        "underage",
        "young-looking",
        "loli",
        "chibi",
        "childlike face",
        "cute child proportions",
        "schoolgirl",
        "oversized anime eyes",
        "doll-like child proportions",
        "multiple girls",
        "2girls",
        "man",
        "male",
        "collage",
        "grid",
        "split screen",
        "diptych",
        "triptych",
        "contact sheet",
        "comic panels",
        "storyboard",
        "ornamental border",
        "decorative border",
        "trading card",
        "card layout",
        "character sheet",
        "reference sheet",
        "text",
        "letters",
        "caption",
        "subtitle",
        "watermark",
        "logo",
        "photo camera",
        "camera equipment",
        "camera lens",
        "tripod",
        "photographer",
        "CCTV camera",
        "surveillance camera",
        "feet close-up",
        "foot close-up",
        "soles close-up",
        "feet only",
        "legs only",
        "body-part focus",
        "body-part fetish framing",
        "disembodied limbs",
        "headless",
        "faceless",
        "cropped head",
        "cropped face",
        "giant feet",
        "tiny head",
        "extreme foreshortening",
        "fisheye",
        "upside down",
        "inverted body",
        "extra limbs",
        "extra fingers",
        "fused body",
        "deformed hands",
        "malformed face",
        "explicit sex act",
        "explicit genital focus",
    )

    private fun roleNegativeTags(role: ImagePromptRole): List<String> = when (role) {
        ImagePromptRole.RECRUIT_CARD -> listOf(
            "headshot",
            "bust portrait",
            "upper body only",
            "cropped feet",
            "feet outside frame",
            "busy action scene",
            "plain white background",
            "shapeless robe",
            "oversized clothing",
        )
        ImagePromptRole.STAFF_CARD -> listOf(
            "headshot",
            "bust portrait",
            "upper body only",
            "cropped feet",
            "feet outside frame",
            "busy action scene",
            "plain white background",
            "shapeless robe",
            "oversized clothing",
        )
        ImagePromptRole.DAY_SCENE -> listOf(
            "studio portrait",
            "plain background",
            "empty white room",
            "isolated object",
            "product photography",
            "generic fantasy tavern",
            "neutral standing pose",
        )
        ImagePromptRole.HOME_SCENE -> listOf(
            "studio portrait",
            "plain background",
            "empty white room",
            "isolated object",
            "product photography",
            "generic fantasy tavern",
        )
    }

    private fun dimensions(role: ImagePromptRole): Pair<Int, Int> = when (role) {
        ImagePromptRole.RECRUIT_CARD, ImagePromptRole.STAFF_CARD -> 768 to 1152
        ImagePromptRole.DAY_SCENE, ImagePromptRole.HOME_SCENE -> 768 to 1024
    }
}
