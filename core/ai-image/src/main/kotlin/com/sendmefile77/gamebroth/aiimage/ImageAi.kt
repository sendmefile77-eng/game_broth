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
        "private room inside a dark fantasy brothel, warm oil lamps, heavy curtains, intimate working atmosphere",
        "private sitting room inside the brothel, velvet settee, mirror, drinks, low amber light",
        "upstairs private room, curtains, worn furniture, soft lamplight, lived-in brothel interior",
        "brothel dressing room, wooden mirror, costume rack, warm candlelight",
        "quiet private lounge inside the brothel, upholstered furniture, curtains, low lamps",
        "bath antechamber inside the brothel, steam, towels, oil lamps, intimate atmosphere",
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
        "natural professional working posture",
        "candid posture during the main event of the shift",
        "focused sensual working posture",
        "natural in-scene body language",
        "confident professional posture",
        "candid movement inside the working room",
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
        "medium-wide composition, character on one third, visible working environment",
        "wide vertical composition, balanced adults and lived-in interior",
    )
    private val homeMoods = listOf(
        "tired but sensually self-possessed",
        "quietly pleased, flirtatious",
        "thoughtful, guarded, intimate",
        "mildly amused, confident sensual presence",
        "focused, practical, teasing undertone",
        "relieved to have a private moment, relaxed and alluring",
        "watchful but relaxed, mature seductive confidence",
    )
    private val dayMoods = listOf(
        "confident sensual focus",
        "warm flirtatious professional energy",
        "mature seductive composure",
        "intimate concentration",
        "playful controlled confidence",
        "tense but self-possessed sensuality",
    )

    fun story(staffId: String, day: Int, ordinal: Int): ScenePlan = home(staffId, day, ordinal)

    fun home(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x51A7))
        return ScenePlan(
            location = homeLocations.random(random),
            action = homeActions.random(random),
            framing = homeFramings.random(random),
            mood = homeMoods.random(random),
        )
    }

    fun report(staffId: String, day: Int, ordinal: Int): ScenePlan = day(staffId, day, ordinal)

    fun day(staffId: String, day: Int, ordinal: Int): ScenePlan {
        val random = Random(seed(staffId, day, ordinal, 0x7E90))
        return ScenePlan(
            location = dayLocations.random(random),
            action = dayActions.random(random),
            framing = dayFramings.random(random),
            mood = dayMoods.random(random),
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
        val clientPresent = resolvedRole == ImagePromptRole.DAY_SCENE && scene.contains("adult client", ignoreCase = true)
        val tags = linkedSetOf<String>()

        tags += baseQualityTags(clientPresent)
        tags += identityTags(staff, profile)
        tags += roleTags(resolvedRole)
        tags += wardrobeTags(profile, currentInventory, resolvedRole)
        tags += eroticTags(resolvedEroticTone, resolvedRole, clientPresent)
        tags += sceneTags(scene)
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

    private fun baseQualityTags(clientPresent: Boolean): List<String> = buildList {
        add("1girl")
        if (clientPresent) {
            add("adult client")
            add("two adults")
        } else {
            add("solo")
        }
        add("mature adult woman")
        add("adult female")
        add("clearly adult facial features")
        add("adult body proportions")
        add("detailed face")
        add("detailed eyes")
        add("natural anatomy")
        add("coherent hands")
        add("high quality illustration")
    }

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
                "provocative brothel work outfit",
                "lingerie-inspired workwear",
                "sensual professional styling",
            )
            ImagePromptRole.HOME_SCENE -> listOf(
                "sensual brothel work outfit",
                "fitted dark fantasy clothing",
            )
        }
    }

    private fun roleTags(role: ImagePromptRole): List<String> = when (role) {
        ImagePromptRole.RECRUIT_CARD -> listOf(
            "full body", "head to toe", "entire body visible", "both feet visible", "standing", "upright pose",
            "front view or gentle three-quarter view", "looking at viewer", "normal eye-level perspective",
            "simple dark fantasy brothel-adjacent interior", "clean background", "first impression",
        )
        ImagePromptRole.STAFF_CARD -> listOf(
            "full body", "head to toe", "entire body visible", "both feet visible", "standing", "elegant contrapposto",
            "front view or gentle three-quarter view", "looking at viewer", "normal eye-level perspective",
            "simple intimate brothel interior", "canonical appearance",
        )
        ImagePromptRole.DAY_SCENE -> listOf(
            "dark fantasy brothel interior", "working brothel scene", "adult erotic profession", "warm oil lamp light",
            "curtains", "worn furniture", "mirror", "private room atmosphere", "environment clearly visible",
        )
        ImagePromptRole.HOME_SCENE -> listOf(
            "dark fantasy brothel interior", "operating establishment", "warm intimate lighting", "curtains", "mirrors",
            "private doors", "reception or common room", "environment clearly visible", "lived-in sensual atmosphere",
        )
    }

    private fun eroticTags(tone: EroticTone, role: ImagePromptRole, clientPresent: Boolean): List<String> {
        val common = mutableListOf(
            "sensual", "seductive", "alluring", "mature erotic atmosphere", "sensual body language",
            "confident adult presence", "intimate warm lighting",
        )
        when (tone) {
            EroticTone.MEDIUM -> common += listOf("flirtatious expression", "suggestive pose", "body-conscious styling")
            EroticTone.HIGH -> common += listOf("strongly seductive pose", "provocative styling", "boudoir mood", "intimate tension")
        }
        when (role) {
            ImagePromptRole.RECRUIT_CARD -> common += "playful confident gaze"
            ImagePromptRole.STAFF_CARD -> common += "memorable seductive gaze"
            ImagePromptRole.DAY_SCENE -> {
                common += "erotic brothel atmosphere"
                if (clientPresent) common += "professional sensual interaction" else common += "sensual solitary moment"
            }
            ImagePromptRole.HOME_SCENE -> common += listOf("erotic establishment ambience", "inviting intimate mood")
        }
        return common
    }

    private fun styleTags(profile: VisualIdentityProfile): List<String> = buildList {
        add("dark fantasy")
        add("mature character design")
        add("semi-realistic mature illustration")
        add("adult dark fantasy character art")
        add("adult proportions")
        addAll(profile.styleTokens.filterNot {
            it.contains("consistent character identity", ignoreCase = true) ||
                it.contains("anime", ignoreCase = true)
        })
    }

    private fun sceneTags(scene: String): List<String> =
        scene.split(',')
            .map { it.trim().trimEnd('.') }
            .filter { it.length in 3..140 }
            .take(32)

    private fun commonNegativeTags(): List<String> = listOf(
        "child", "teen", "underage", "young-looking", "loli", "chibi", "childlike face", "cute child proportions",
        "schoolgirl", "oversized anime eyes", "doll-like child proportions", "collage", "grid", "split screen",
        "diptych", "triptych", "contact sheet", "comic panels", "storyboard", "ornamental border", "decorative border",
        "trading card", "card layout", "character sheet", "reference sheet", "text", "letters", "caption", "subtitle",
        "watermark", "logo", "photo camera", "camera equipment", "camera lens", "tripod", "photographer", "CCTV camera",
        "surveillance camera", "feet close-up", "foot close-up", "soles close-up", "feet only", "legs only",
        "body-part focus", "body-part fetish framing", "disembodied limbs", "headless", "faceless", "cropped head",
        "cropped face", "giant feet", "tiny head", "extreme foreshortening", "fisheye", "upside down", "inverted body",
        "extra limbs", "extra fingers", "fused body", "deformed hands", "malformed face", "explicit sex act",
        "explicit intercourse", "explicit genital focus",
    )

    private fun roleNegativeTags(role: ImagePromptRole): List<String> = when (role) {
        ImagePromptRole.RECRUIT_CARD, ImagePromptRole.STAFF_CARD -> listOf(
            "2girls", "multiple people", "man", "male", "headshot", "bust portrait", "upper body only", "cropped feet",
            "feet outside frame", "busy action scene", "plain white background", "shapeless robe", "oversized clothing",
        )
        ImagePromptRole.DAY_SCENE -> listOf(
            "studio portrait", "plain background", "empty white room", "isolated object", "product photography",
            "generic fantasy tavern", "neutral standing pose",
        )
        ImagePromptRole.HOME_SCENE -> listOf(
            "studio portrait", "plain background", "empty white room", "isolated object", "product photography",
            "generic fantasy tavern",
        )
    }

    private fun dimensions(role: ImagePromptRole): Pair<Int, Int> = when (role) {
        ImagePromptRole.RECRUIT_CARD, ImagePromptRole.STAFF_CARD -> 768 to 1152
        ImagePromptRole.DAY_SCENE, ImagePromptRole.HOME_SCENE -> 768 to 1024
    }
}
