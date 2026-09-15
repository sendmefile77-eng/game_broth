package com.sendmefile77.gamebroth.model

enum class GalleryFrameRole { PORTRAIT, SCENE, EVENT }

data class VisualIdentityProfile(
    val staffId: String,
    val revision: Int = 1,
    val locked: Boolean = true,
    val ageBand: String,
    val build: String,
    val skinTone: String,
    val hair: String,
    val eyes: String,
    val face: String,
    val distinctiveMarks: List<String> = emptyList(),
    val speciesTokens: List<String> = emptyList(),
    val bodyPlan: String = "adult humanoid; two arms; two legs; one head",
    val wardrobeTokens: List<String> = emptyList(),
    val styleTokens: List<String> = emptyList(),
    val canonicalFrameId: String? = null,
    val updatedAtDay: Int = 1,
) {
    init {
        require(staffId.isNotBlank())
        require(revision >= 1)
        require(ageBand.isNotBlank())
        require(build.isNotBlank())
        require(skinTone.isNotBlank())
        require(hair.isNotBlank())
        require(eyes.isNotBlank())
        require(face.isNotBlank())
        require(bodyPlan.isNotBlank())
        require(updatedAtDay >= 1)
    }

    fun stableIdentityTokens(): List<String> = buildList {
        add(ageBand)
        add(build)
        add(skinTone)
        add(hair)
        add(eyes)
        add(face)
        addAll(speciesTokens)
        addAll(distinctiveMarks)
        add(bodyPlan)
    }
}

data class GalleryFrame(
    val id: String,
    val staffId: String,
    val day: Int,
    val role: GalleryFrameRole,
    val localPath: String,
    val prompt: String,
    val negativePrompt: String,
    val seed: Long? = null,
    val width: Int,
    val height: Int,
    val referenceFrameId: String? = null,
    val profileRevision: Int,
    val createdAtEpochMs: Long,
    val canonical: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(staffId.isNotBlank())
        require(day >= 1)
        require(localPath.isNotBlank())
        require(prompt.isNotBlank())
        require(width > 0 && height > 0)
        require(profileRevision >= 1)
        require(createdAtEpochMs >= 0)
    }
}
