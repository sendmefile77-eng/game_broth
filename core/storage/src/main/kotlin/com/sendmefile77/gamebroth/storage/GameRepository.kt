package com.sendmefile77.gamebroth.storage

import com.sendmefile77.gamebroth.model.*

interface GameRepository {
    fun loadOrCreate(): GameState
    fun saveState(state: GameState)
    fun appendMemory(memory: StaffMemory)
    fun memoriesForStaff(staffId: String, limit: Int = 40): List<StaffMemory>
    fun appendWorldEvent(event: WorldEvent)
    fun recentWorldEvents(limit: Int = 80): List<WorldEvent>
    fun saveDailyReport(report: DailyReport)
    fun latestDailyReport(): DailyReport?

    fun saveVisualProfile(profile: VisualIdentityProfile)
    fun visualProfile(staffId: String): VisualIdentityProfile?
    fun saveGalleryFrame(frame: GalleryFrame)
    fun galleryForStaff(staffId: String, limit: Int = 200): List<GalleryFrame>
    fun galleryFrame(frameId: String): GalleryFrame?
    fun setCanonicalFrame(staffId: String, frameId: String): VisualIdentityProfile?
    fun setVisualIdentityLocked(staffId: String, locked: Boolean): VisualIdentityProfile?
}
