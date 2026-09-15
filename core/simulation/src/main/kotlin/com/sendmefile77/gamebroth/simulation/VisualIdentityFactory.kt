package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.VisualIdentityProfile
import com.sendmefile77.gamebroth.model.StaffMember
import kotlin.random.Random

object VisualIdentityFactory {
    fun fromStaff(member: StaffMember, worldSeed: Long, day: Int): VisualIdentityProfile = fromFields(
        staffId = member.id,
        speciesName = member.species,
        ageYears = member.ageYears,
        worldSeed = worldSeed,
        day = day,
    )

    fun fromCandidate(candidate: RecruitCandidate, worldSeed: Long, day: Int): VisualIdentityProfile = fromFields(
        staffId = candidate.id,
        speciesName = candidate.species,
        ageYears = candidate.ageYears,
        worldSeed = worldSeed,
        day = day,
    )

    private fun fromFields(staffId: String, speciesName: String, ageYears: Int, worldSeed: Long, day: Int): VisualIdentityProfile {
        val random = Random(worldSeed xor staffId.hashCode().toLong() xor 0x56495355414CL)
        val species = speciesVisuals(speciesName)
        return VisualIdentityProfile(
            staffId = staffId,
            revision = 1,
            locked = true,
            ageBand = "adult $ageYears years old",
            build = BUILD.random(random),
            skinTone = species.skin.random(random),
            hair = HAIR.random(random),
            eyes = EYES.random(random),
            face = FACE.random(random),
            distinctiveMarks = listOf(MARKS.random(random)),
            speciesTokens = species.tokens,
            bodyPlan = species.bodyPlan,
            wardrobeTokens = emptyList(),
            styleTokens = listOf("dark fantasy", "consistent character identity"),
            updatedAtDay = day,
        )
    }

    private data class SpeciesVisuals(val skin: List<String>, val tokens: List<String>, val bodyPlan: String)

    private fun speciesVisuals(species: String): SpeciesVisuals = when {
        species.contains("каменнокож", true) -> SpeciesVisuals(
            listOf("warm granite-gray skin", "pale basalt skin", "smoky stone-toned skin"),
            listOf("subtle stone texture at shoulders", "human facial anatomy"),
            "adult humanoid; two arms; two legs; one head; subtle stone-textured skin",
        )
        species.contains("болот", true) -> SpeciesVisuals(
            listOf("olive green-brown skin", "cool moss-toned skin", "pale reed-gold skin"),
            listOf("faint iridescent freckles", "slightly reflective skin"),
            "adult humanoid; two arms; two legs; one head; subtle amphibious fantasy traits",
        )
        species.contains("меднокров", true) -> SpeciesVisuals(
            listOf("copper-brown skin", "warm bronze skin", "deep umber skin with copper undertone"),
            listOf("warm metallic undertone", "human facial anatomy"),
            "adult humanoid; two arms; two legs; one head",
        )
        species.contains("амфиби", true) -> SpeciesVisuals(
            listOf("blue-gray skin", "sea-green skin", "pearl-gray skin"),
            listOf("subtle gill lines at neck", "slightly webbed fingers"),
            "adult sentient humanoid amphibian; two arms; two legs; one head; subtle neck gills",
        )
        species.contains("фарфоров", true) -> SpeciesVisuals(
            listOf("ivory porcelain skin", "warm cream porcelain skin", "pale rose porcelain skin"),
            listOf("fine porcelain seam lines", "glossy ceramic-like skin highlights"),
            "adult sentient humanoid; two arms; two legs; one head; porcelain-like skin",
        )
        species.contains("черниль", true) -> SpeciesVisuals(
            listOf("cool brown skin with ink-blue undertone", "deep brown skin", "pale skin with blue-black undertone"),
            listOf("faint ink-like birthmark", "human facial anatomy"),
            "adult humanoid; two arms; two legs; one head",
        )
        else -> SpeciesVisuals(listOf("natural warm skin tone", "natural cool skin tone"), emptyList(), "adult humanoid; two arms; two legs; one head")
    }

    private val BUILD = listOf("tall lean build", "compact athletic build", "soft curvy build", "strong broad-shouldered build", "slender long-limbed build")
    private val HAIR = listOf("long black wavy hair", "short dark auburn hair", "thick chestnut curls", "straight ash-brown hair to the shoulders", "dark braided hair")
    private val EYES = listOf("amber eyes", "gray-green eyes", "dark brown eyes", "pale blue eyes", "hazel eyes")
    private val FACE = listOf("sharp cheekbones and a narrow jaw", "round face with strong brows", "oval face with a straight nose", "angular face with full lips", "long face with a pronounced jawline")
    private val MARKS = listOf("small scar through the left eyebrow", "tiny beauty mark under the right eye", "thin geometric tattoo at the collarbone", "small notch in the right eyebrow", "faint crescent birthmark at the temple")
}
