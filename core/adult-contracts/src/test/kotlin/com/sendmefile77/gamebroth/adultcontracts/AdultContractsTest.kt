package com.sendmefile77.gamebroth.adultcontracts

import org.junit.Test

class AdultContractsTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMinorParticipant() {
        AdultParticipantRef("x", ageYears = 17, consentConfirmed = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingConsent() {
        AdultParticipantRef("x", ageYears = 20, consentConfirmed = false)
    }
}
