package com.sendmefile77.gamebroth.aiimage

import com.sendmefile77.gamebroth.model.GalleryFrameRole
import com.sendmefile77.gamebroth.model.InventoryItem
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.VisualIdentityProfile

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

data class StaffFramePrompt(
    val role: GalleryFrameRole,
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
)

object VisualPromptBuilder {
    fun build(
        staff: StaffMember,
        profile: VisualIdentityProfile,
        role: GalleryFrameRole,
        scene: String,
        currentInventory: List<InventoryItem> = staff.inventory,
    ): StaffFramePrompt {
        val identity = profile.stableIdentityTokens().joinToString(", ")
        val wardrobe = (profile.wardrobeTokens + currentInventory.flatMap { item ->
            buildList {
                if ("clothing" in item.tags || "jewelry" in item.tags || "visual" in item.tags) add(item.name)
            }
        }).distinct().joinToString(", ").ifBlank { "simple dark-fantasy work clothes" }
        val style = profile.styleTokens.joinToString(", ").ifBlank { "dark fantasy" }
        val roleText = when (role) {
            GalleryFrameRole.PORTRAIT -> "SINGLE CONTINUOUS IMAGE, exactly one fictional adult woman, full-height casting portrait, head-to-feet, face clearly visible, centered subject, one camera, one background"
            GalleryFrameRole.SCENE -> "SINGLE CONTINUOUS IMAGE, exactly one fictional adult woman, one new story scene, one camera, one background; preserve identity but create a NEW pose, NEW framing and NEW environment"
            GalleryFrameRole.EVENT -> "SINGLE CONTINUOUS IMAGE, exactly one fictional adult woman, wide narrative event frame, one camera, one environment; preserve identity but create a NEW composition"
        }
        val prompt = "$roleText. Character identity MUST remain consistent: $identity. Species: ${staff.species}. Current wardrobe and accessories: $wardrobe. Scene: $scene. Style: $style. If an identity reference image is supplied, use it ONLY for character identity; DO NOT copy its pose, framing, background, props, panel layout or composition. Natural anatomy, coherent hands, consistent face, recognizable hair and marks."
        val layoutNegative = "collage, grid, split screen, diptych, triptych, contact sheet, comic panels, storyboard, multiple panels, repeated frame, duplicated composition"
        val portraitCountNegative = if (role == GalleryFrameRole.PORTRAIT) ", multiple people, two people, man, male" else ""
        val negative = "child, teen, underage, young-looking, $layoutNegative$portraitCountNegative, duplicate person, cloned face, extra limbs, extra fingers, fused body, deformed hands, missing hands, malformed face, inconsistent hair, inconsistent eye color"
        val (w, h) = when (role) {
            GalleryFrameRole.EVENT -> 1152 to 768
            else -> 768 to 1024
        }
        return StaffFramePrompt(role, prompt, negative, w, h)
    }
}
