package com.sendmefile77.gamebroth.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sendmefile77.gamebroth.model.*

class SqliteGameRepository(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
), GameRepository {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE game_state(
                id INTEGER PRIMARY KEY CHECK(id = 1),
                schema_version INTEGER NOT NULL,
                world_seed INTEGER NOT NULL,
                current_day INTEGER NOT NULL,
                establishment_id TEXT NOT NULL,
                establishment_name TEXT NOT NULL,
                establishment_level INTEGER NOT NULL,
                treasury INTEGER NOT NULL,
                debt INTEGER NOT NULL,
                public_reputation INTEGER NOT NULL,
                heat INTEGER NOT NULL,
                luxury INTEGER NOT NULL,
                secrecy INTEGER NOT NULL,
                arcane INTEGER NOT NULL,
                political_influence INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE staff(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                species TEXT NOT NULL,
                age_years INTEGER NOT NULL CHECK(age_years >= 18),
                level INTEGER NOT NULL,
                xp INTEGER NOT NULL,
                personal_money INTEGER NOT NULL,
                loyalty INTEGER NOT NULL,
                stress INTEGER NOT NULL,
                fatigue INTEGER NOT NULL,
                health INTEGER NOT NULL,
                reputation INTEGER NOT NULL,
                status TEXT NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE TABLE staff_traits(staff_id TEXT NOT NULL REFERENCES staff(id) ON DELETE CASCADE, trait TEXT NOT NULL, PRIMARY KEY(staff_id, trait))")
        db.execSQL("CREATE TABLE staff_skills(staff_id TEXT NOT NULL REFERENCES staff(id) ON DELETE CASCADE, code TEXT NOT NULL, level INTEGER NOT NULL, xp INTEGER NOT NULL, PRIMARY KEY(staff_id, code))")
        db.execSQL("CREATE TABLE inventory(item_id TEXT NOT NULL, staff_id TEXT NOT NULL REFERENCES staff(id) ON DELETE CASCADE, name TEXT NOT NULL, quantity INTEGER NOT NULL, PRIMARY KEY(staff_id, item_id))")
        db.execSQL("CREATE TABLE inventory_tags(staff_id TEXT NOT NULL, item_id TEXT NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(staff_id, item_id, tag), FOREIGN KEY(staff_id, item_id) REFERENCES inventory(staff_id, item_id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE quests(id TEXT PRIMARY KEY, title TEXT NOT NULL, summary TEXT NOT NULL, status TEXT NOT NULL, progress INTEGER NOT NULL, target INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE factions(id TEXT PRIMARY KEY, name TEXT NOT NULL, relation INTEGER NOT NULL, influence INTEGER NOT NULL, attention INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE artifacts(id TEXT PRIMARY KEY, name TEXT NOT NULL, charges INTEGER NOT NULL, max_charges INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE artifact_tags(artifact_id TEXT NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE, tag TEXT NOT NULL, PRIMARY KEY(artifact_id, tag))")
        db.execSQL("CREATE TABLE secrets(id TEXT PRIMARY KEY, title TEXT NOT NULL, owner_npc_id TEXT, leverage INTEGER NOT NULL, exposed INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE staff_memories(
                id TEXT PRIMARY KEY,
                staff_id TEXT NOT NULL,
                day INTEGER NOT NULL,
                category TEXT NOT NULL,
                summary TEXT NOT NULL,
                importance INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_staff_memories_staff_day ON staff_memories(staff_id, day DESC, created_at DESC)")
        db.execSQL(
            """CREATE TABLE world_events(
                id TEXT PRIMARY KEY,
                day INTEGER NOT NULL,
                type TEXT NOT NULL,
                summary TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_world_events_day ON world_events(day DESC, created_at DESC)")
        createV2Tables(db)
        createV3Tables(db)
        createV4Tables(db)
        createV5Tables(db)
        createV6Tables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createV2Tables(db)
        if (oldVersion < 3) createV3Tables(db)
        if (oldVersion < 4) createV4Tables(db)
        if (oldVersion < 5) createV5Tables(db)
        if (oldVersion < 6) createV6Tables(db)
        if (newVersion > 6) error("No migration path from $oldVersion to $newVersion yet")
    }

    override fun loadOrCreate(): GameState {
        val loaded = loadState()
        if (loaded != null) {
            if (loaded.schemaVersion >= 6) return loaded
            return loaded.copy(schemaVersion = 6).also(::saveState)
        }
        return GameState.newGame().also(::saveState)
    }

    override fun saveState(state: GameState) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val e = state.establishment
            db.insertWithOnConflict("game_state", null, ContentValues().apply {
                put("id", 1); put("schema_version", state.schemaVersion); put("world_seed", state.worldSeed); put("current_day", state.currentDay)
                put("establishment_id", e.id); put("establishment_name", e.name); put("establishment_level", e.level)
                put("treasury", e.treasury); put("debt", e.debt); put("public_reputation", e.publicReputation); put("heat", e.heat)
                put("luxury", e.luxury); put("secrecy", e.secrecy); put("arcane", e.arcane); put("political_influence", e.politicalInfluence)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.insertWithOnConflict("establishment_policy", null, ContentValues().apply {
                put("id", 1); put("pricing_policy", e.pricingPolicy.name); put("workload_policy", e.workloadPolicy.name)
            }, SQLiteDatabase.CONFLICT_REPLACE)

            db.delete("staff_relations", null, null)
            db.delete("staff_goals", null, null)
            db.delete("inventory_tags", null, null)
            db.delete("inventory", null, null)
            db.delete("staff_preferences", null, null)
            db.delete("staff_skills", null, null)
            db.delete("staff_traits", null, null)
            db.delete("staff", null, null)
            db.delete("quests", null, null)
            db.delete("factions", null, null)
            db.delete("artifact_tags", null, null)
            db.delete("artifacts", null, null)
            db.delete("secrets", null, null)

            state.staff.forEach { insertStaff(db, it) }
            state.staffRelations.forEach { relation ->
                db.insertOrThrow("staff_relations", null, ContentValues().apply {
                    put("first_staff_id", relation.firstStaffId); put("second_staff_id", relation.secondStaffId)
                    put("affinity", relation.affinity); put("tension", relation.tension); put("updated_day", relation.updatedDay)
                })
            }
            state.staffGoals.forEach { goal ->
                db.insertOrThrow("staff_goals", null, ContentValues().apply {
                    put("id", goal.id); put("staff_id", goal.staffId); put("kind", goal.kind.name); put("title", goal.title)
                    put("target", goal.target); put("progress", goal.progress); put("target_code", goal.targetCode)
                    put("created_day", goal.createdDay); put("deadline_day", goal.deadlineDay); put("status", goal.status.name)
                })
            }
            state.quests.forEach { quest ->
                db.insertOrThrow("quests", null, ContentValues().apply {
                    put("id", quest.id); put("title", quest.title); put("summary", quest.summary)
                    put("status", quest.status.name); put("progress", quest.progress); put("target", quest.target)
                })
            }
            state.factions.forEach { faction ->
                db.insertOrThrow("factions", null, ContentValues().apply {
                    put("id", faction.id); put("name", faction.name); put("relation", faction.relation)
                    put("influence", faction.influence); put("attention", faction.attention)
                })
            }
            state.artifacts.forEach { artifact ->
                db.insertOrThrow("artifacts", null, ContentValues().apply {
                    put("id", artifact.id); put("name", artifact.name); put("charges", artifact.charges); put("max_charges", artifact.maxCharges)
                })
                artifact.tags.forEach { tag -> db.insertOrThrow("artifact_tags", null, ContentValues().apply { put("artifact_id", artifact.id); put("tag", tag) }) }
            }
            state.secrets.forEach { secret ->
                db.insertOrThrow("secrets", null, ContentValues().apply {
                    put("id", secret.id); put("title", secret.title); put("owner_npc_id", secret.ownerNpcId)
                    put("leverage", secret.leverage); put("exposed", if (secret.exposed) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun appendMemory(memory: StaffMemory) {
        writableDatabase.insertWithOnConflict("staff_memories", null, ContentValues().apply {
            put("id", memory.id); put("staff_id", memory.staffId); put("day", memory.day)
            put("category", memory.category); put("summary", memory.summary); put("importance", memory.importance); put("created_at", memory.createdAtEpochMs)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun memoriesForStaff(staffId: String, limit: Int): List<StaffMemory> {
        require(limit in 1..500)
        return readableDatabase.query(
            "staff_memories", arrayOf("id", "staff_id", "day", "category", "summary", "importance", "created_at"),
            "staff_id = ?", arrayOf(staffId), null, null, "importance DESC, day DESC, created_at DESC", limit.toString(),
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add(StaffMemory(
                id = cursor.getString(0), staffId = cursor.getString(1), day = cursor.getInt(2), category = cursor.getString(3),
                summary = cursor.getString(4), importance = cursor.getInt(5), createdAtEpochMs = cursor.getLong(6),
            ))
        }}
    }

    override fun appendWorldEvent(event: WorldEvent) {
        writableDatabase.insertWithOnConflict("world_events", null, ContentValues().apply {
            put("id", event.id); put("day", event.day); put("type", event.type); put("summary", event.summary)
            put("payload", event.payload); put("created_at", event.createdAtEpochMs)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun recentWorldEvents(limit: Int): List<WorldEvent> {
        require(limit in 1..500)
        return readableDatabase.query(
            "world_events", arrayOf("id", "day", "type", "summary", "payload", "created_at"),
            null, null, null, null, "day DESC, created_at DESC", limit.toString(),
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add(WorldEvent(
                id = cursor.getString(0), day = cursor.getInt(1), type = cursor.getString(2), summary = cursor.getString(3),
                payload = cursor.getString(4), createdAtEpochMs = cursor.getLong(5),
            ))
        }}
    }

    override fun saveDailyReport(report: DailyReport) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("work_encounters", "day = ?", arrayOf(report.day.toString()))
            db.delete("staff_day_summaries", "day = ?", arrayOf(report.day.toString()))
            db.delete("daily_reports", "day = ?", arrayOf(report.day.toString()))
            db.insertOrThrow("daily_reports", null, ContentValues().apply {
                put("day", report.day); put("gross_revenue", report.grossRevenue); put("upkeep", report.upkeep)
                put("treasury_after", report.treasuryAfter); put("debt_delta", report.debtDelta); put("created_at", report.createdAtEpochMs)
            })
            report.staffReports.forEach { sr ->
                db.insertOrThrow("staff_day_summaries", null, ContentValues().apply {
                    put("day", sr.day); put("staff_id", sr.staffId); put("staff_name", sr.staffName)
                    put("level_before", sr.levelBefore); put("level_after", sr.levelAfter)
                    put("business_revenue", sr.businessRevenue); put("personal_revenue", sr.personalRevenue)
                    put("purchase_item_id", sr.purchase?.item?.id); put("purchase_name", sr.purchase?.item?.name)
                    put("purchase_price", sr.purchase?.price); put("purchase_reason", sr.purchase?.reason); put("incident", sr.incident)
                })
                sr.encounters.forEach { insertEncounter(db, it) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun latestDailyReport(): DailyReport? {
        val db = readableDatabase
        val h = db.query("daily_reports", null, null, null, null, null, "day DESC", "1").use { c ->
            if (!c.moveToFirst()) return null
            ReportHeader(c.int("day"), c.long("gross_revenue"), c.long("upkeep"), c.long("treasury_after"), c.long("debt_delta"), c.long("created_at"))
        }
        val staffReports = db.query("staff_day_summaries", null, "day = ?", arrayOf(h.day.toString()), null, null, "staff_name").use { c ->
            buildList { while (c.moveToNext()) {
                val staffId = c.string("staff_id")
                val purchase = c.nullableString("purchase_name")?.let { name ->
                    PersonalPurchase(InventoryItem(c.nullableString("purchase_item_id") ?: "historic-$staffId-${h.day}", name), c.nullableLong("purchase_price") ?: 1L, c.nullableString("purchase_reason") ?: "личная покупка")
                }
                add(StaffDayReport(h.day, staffId, c.string("staff_name"), c.int("level_before"), c.int("level_after"), loadEncounters(db, h.day, staffId), c.long("business_revenue"), c.long("personal_revenue"), purchase, c.nullableString("incident")))
            }}
        }
        return DailyReport(h.day, staffReports, h.gross, h.upkeep, h.treasury, h.debt, h.createdAt)
    }

    override fun saveStaffRequest(request: StaffRequest) {
        writableDatabase.insertWithOnConflict("staff_requests", null, ContentValues().apply {
            put("id", request.id); put("staff_id", request.staffId); put("staff_name", request.staffName)
            put("created_day", request.createdDay); put("expires_day", request.expiresDay); put("kind", request.kind.name)
            put("title", request.title); put("body", request.body); put("cost", request.cost)
            put("loyalty_accept", request.loyaltyOnAccept); put("loyalty_refuse", request.loyaltyOnRefuse)
            put("stress_accept", request.stressOnAccept); put("stress_refuse", request.stressOnRefuse); put("status", request.status.name)
            request.resolvedDay?.let { put("resolved_day", it) } ?: putNull("resolved_day")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun pendingStaffRequests(): List<StaffRequest> = readableDatabase.query(
        "staff_requests", null, "status = ?", arrayOf(StaffRequestStatus.PENDING.name), null, null,
        "expires_day ASC, created_day ASC, staff_name ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(staffRequestFromCursor(cursor)) } }

    override fun recentStaffRequests(limit: Int): List<StaffRequest> {
        require(limit in 1..500)
        return readableDatabase.query("staff_requests", null, null, null, null, null, "created_day DESC, staff_name ASC", limit.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(staffRequestFromCursor(cursor)) } }
    }

    override fun staffRequestsForStaff(staffId: String, limit: Int): List<StaffRequest> {
        require(limit in 1..500)
        return readableDatabase.query("staff_requests", null, "staff_id = ?", arrayOf(staffId), null, null, "created_day DESC, id DESC", limit.toString())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(staffRequestFromCursor(cursor)) } }
    }

    private fun insertEncounter(db: SQLiteDatabase, e: WorkEncounter) {
        val c = e.client
        db.insertOrThrow("work_encounters", null, ContentValues().apply {
            put("id", e.id); put("day", e.day); put("staff_id", e.staffId); put("client_id", c.id); put("client_name", c.displayName); put("client_archetype", c.archetype)
            put("client_age", c.ageYears); put("client_wealth", c.wealth); put("client_patience", c.patience); put("client_discretion", c.discretion); put("client_interests", c.interests.sorted().joinToString("|"))
            put("service_code", e.serviceCode); put("outcome", e.outcome.name); put("summary", e.summary); put("gross_revenue", e.grossRevenue); put("staff_cut", e.staffCut); put("business_cut", e.businessCut)
            put("trained_skill", e.trainedSkill); put("skill_xp", e.skillXp); put("fatigue_delta", e.fatigueDelta); put("stress_delta", e.stressDelta); put("health_delta", e.healthDelta); put("incident", e.incident)
        })
    }

    private fun loadEncounters(db: SQLiteDatabase, day: Int, staffId: String): List<WorkEncounter> = db.query(
        "work_encounters", null, "day = ? AND staff_id = ?", arrayOf(day.toString(), staffId), null, null, "id",
    ).use { c -> buildList { while (c.moveToNext()) {
        val client = ClientProfile(c.string("client_id"), c.string("client_name"), c.string("client_archetype"), c.int("client_age"), c.int("client_wealth"), c.int("client_patience"), c.int("client_discretion"), c.string("client_interests").split('|').filter(String::isNotBlank).toSet())
        add(WorkEncounter(c.string("id"), c.int("day"), c.string("staff_id"), client, c.string("service_code"), EncounterOutcome.valueOf(c.string("outcome")), c.string("summary"), c.long("gross_revenue"), c.long("staff_cut"), c.long("business_cut"), c.string("trained_skill"), c.int("skill_xp"), c.int("fatigue_delta"), c.int("stress_delta"), c.int("health_delta"), c.nullableString("incident")))
    }} }

    override fun saveVisualProfile(profile: VisualIdentityProfile) {
        writableDatabase.insertWithOnConflict("visual_identity", null, ContentValues().apply {
            put("staff_id", profile.staffId); put("revision", profile.revision); put("locked", if (profile.locked) 1 else 0)
            put("age_band", profile.ageBand); put("build_desc", profile.build); put("skin_tone", profile.skinTone); put("hair", profile.hair)
            put("eyes", profile.eyes); put("face", profile.face); put("distinctive_marks", profile.distinctiveMarks.joinToString("|"))
            put("species_tokens", profile.speciesTokens.joinToString("|")); put("body_plan", profile.bodyPlan)
            put("wardrobe_tokens", profile.wardrobeTokens.joinToString("|")); put("style_tokens", profile.styleTokens.joinToString("|"))
            put("canonical_frame_id", profile.canonicalFrameId); put("updated_day", profile.updatedAtDay)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun visualProfile(staffId: String): VisualIdentityProfile? = readableDatabase.query(
        "visual_identity", null, "staff_id = ?", arrayOf(staffId), null, null, null,
    ).use { c -> if (!c.moveToFirst()) null else visualProfileFromCursor(c) }

    override fun saveGalleryFrame(frame: GalleryFrame) {
        writableDatabase.insertWithOnConflict("gallery_frames", null, ContentValues().apply {
            put("id", frame.id); put("staff_id", frame.staffId); put("day", frame.day); put("role", frame.role.name)
            put("local_path", frame.localPath); put("prompt", frame.prompt); put("negative_prompt", frame.negativePrompt)
            put("seed", frame.seed); put("width", frame.width); put("height", frame.height); put("reference_frame_id", frame.referenceFrameId)
            put("profile_revision", frame.profileRevision); put("created_at", frame.createdAtEpochMs); put("is_canonical", if (frame.canonical) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun galleryForStaff(staffId: String, limit: Int): List<GalleryFrame> {
        require(limit in 1..1000)
        return readableDatabase.query("gallery_frames", null, "staff_id = ?", arrayOf(staffId), null, null, "day DESC, created_at DESC", limit.toString())
            .use { c -> buildList { while (c.moveToNext()) add(galleryFrameFromCursor(c)) } }
    }

    override fun galleryFrame(frameId: String): GalleryFrame? = readableDatabase.query(
        "gallery_frames", null, "id = ?", arrayOf(frameId), null, null, null,
    ).use { c -> if (!c.moveToFirst()) null else galleryFrameFromCursor(c) }

    override fun setCanonicalFrame(staffId: String, frameId: String): VisualIdentityProfile? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val target = db.query("gallery_frames", arrayOf("id"), "id = ? AND staff_id = ?", arrayOf(frameId, staffId), null, null, null).use { it.moveToFirst() }
            if (!target) return null
            db.update("gallery_frames", ContentValues().apply { put("is_canonical", 0) }, "staff_id = ?", arrayOf(staffId))
            db.update("gallery_frames", ContentValues().apply { put("is_canonical", 1) }, "id = ?", arrayOf(frameId))
            db.update("visual_identity", ContentValues().apply { put("canonical_frame_id", frameId) }, "staff_id = ?", arrayOf(staffId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return visualProfile(staffId)
    }

    override fun setVisualIdentityLocked(staffId: String, locked: Boolean): VisualIdentityProfile? {
        writableDatabase.update("visual_identity", ContentValues().apply { put("locked", if (locked) 1 else 0) }, "staff_id = ?", arrayOf(staffId))
        return visualProfile(staffId)
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS staff_preferences(staff_id TEXT NOT NULL REFERENCES staff(id) ON DELETE CASCADE, code TEXT NOT NULL, stance TEXT NOT NULL, PRIMARY KEY(staff_id, code))")
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_reports(day INTEGER PRIMARY KEY, gross_revenue INTEGER NOT NULL, upkeep INTEGER NOT NULL, treasury_after INTEGER NOT NULL, debt_delta INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS staff_day_summaries(day INTEGER NOT NULL, staff_id TEXT NOT NULL, staff_name TEXT NOT NULL, level_before INTEGER NOT NULL, level_after INTEGER NOT NULL, business_revenue INTEGER NOT NULL, personal_revenue INTEGER NOT NULL, purchase_item_id TEXT, purchase_name TEXT, purchase_price INTEGER, purchase_reason TEXT, incident TEXT, PRIMARY KEY(day, staff_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS work_encounters(id TEXT PRIMARY KEY, day INTEGER NOT NULL, staff_id TEXT NOT NULL, client_id TEXT NOT NULL, client_name TEXT NOT NULL, client_archetype TEXT NOT NULL, client_age INTEGER NOT NULL CHECK(client_age >= 18), client_wealth INTEGER NOT NULL, client_patience INTEGER NOT NULL, client_discretion INTEGER NOT NULL, client_interests TEXT NOT NULL, service_code TEXT NOT NULL, outcome TEXT NOT NULL, summary TEXT NOT NULL, gross_revenue INTEGER NOT NULL, staff_cut INTEGER NOT NULL, business_cut INTEGER NOT NULL, trained_skill TEXT NOT NULL, skill_xp INTEGER NOT NULL, fatigue_delta INTEGER NOT NULL, stress_delta INTEGER NOT NULL, health_delta INTEGER NOT NULL, incident TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_encounters_day_staff ON work_encounters(day DESC, staff_id)")
    }

    private fun createV3Tables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS visual_identity(
            staff_id TEXT PRIMARY KEY, revision INTEGER NOT NULL, locked INTEGER NOT NULL, age_band TEXT NOT NULL,
            build_desc TEXT NOT NULL, skin_tone TEXT NOT NULL, hair TEXT NOT NULL, eyes TEXT NOT NULL, face TEXT NOT NULL,
            distinctive_marks TEXT NOT NULL, species_tokens TEXT NOT NULL, body_plan TEXT NOT NULL, wardrobe_tokens TEXT NOT NULL,
            style_tokens TEXT NOT NULL, canonical_frame_id TEXT, updated_day INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS gallery_frames(
            id TEXT PRIMARY KEY, staff_id TEXT NOT NULL, day INTEGER NOT NULL, role TEXT NOT NULL, local_path TEXT NOT NULL,
            prompt TEXT NOT NULL, negative_prompt TEXT NOT NULL, seed INTEGER, width INTEGER NOT NULL, height INTEGER NOT NULL,
            reference_frame_id TEXT, profile_revision INTEGER NOT NULL, created_at INTEGER NOT NULL, is_canonical INTEGER NOT NULL DEFAULT 0
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gallery_staff_day ON gallery_frames(staff_id, day DESC, created_at DESC)")
    }

    private fun createV4Tables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS staff_requests(
            id TEXT PRIMARY KEY, staff_id TEXT NOT NULL, staff_name TEXT NOT NULL, created_day INTEGER NOT NULL,
            expires_day INTEGER NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, cost INTEGER NOT NULL,
            loyalty_accept INTEGER NOT NULL, loyalty_refuse INTEGER NOT NULL, stress_accept INTEGER NOT NULL, stress_refuse INTEGER NOT NULL,
            status TEXT NOT NULL, resolved_day INTEGER
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_staff_requests_status_day ON staff_requests(status, expires_day ASC, created_day DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_staff_requests_staff_day ON staff_requests(staff_id, created_day DESC)")
    }

    private fun createV5Tables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS staff_relations(
            first_staff_id TEXT NOT NULL, second_staff_id TEXT NOT NULL, affinity INTEGER NOT NULL,
            tension INTEGER NOT NULL, updated_day INTEGER NOT NULL, PRIMARY KEY(first_staff_id, second_staff_id)
        )""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS staff_goals(
            id TEXT PRIMARY KEY, staff_id TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL,
            target INTEGER NOT NULL, progress INTEGER NOT NULL, target_code TEXT, created_day INTEGER NOT NULL,
            deadline_day INTEGER NOT NULL, status TEXT NOT NULL
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_staff_goals_staff_status ON staff_goals(staff_id, status, deadline_day)")
    }

    private fun createV6Tables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS establishment_policy(
            id INTEGER PRIMARY KEY CHECK(id = 1),
            pricing_policy TEXT NOT NULL,
            workload_policy TEXT NOT NULL
        )""".trimIndent())
    }

    private fun staffRequestFromCursor(c: android.database.Cursor) = StaffRequest(
        id = c.string("id"), staffId = c.string("staff_id"), staffName = c.string("staff_name"), createdDay = c.int("created_day"),
        expiresDay = c.int("expires_day"), kind = StaffRequestKind.valueOf(c.string("kind")), title = c.string("title"), body = c.string("body"),
        cost = c.long("cost"), loyaltyOnAccept = c.int("loyalty_accept"), loyaltyOnRefuse = c.int("loyalty_refuse"),
        stressOnAccept = c.int("stress_accept"), stressOnRefuse = c.int("stress_refuse"), status = StaffRequestStatus.valueOf(c.string("status")),
        resolvedDay = c.nullableInt("resolved_day"),
    )

    private fun visualProfileFromCursor(c: android.database.Cursor) = VisualIdentityProfile(
        staffId = c.string("staff_id"), revision = c.int("revision"), locked = c.int("locked") != 0,
        ageBand = c.string("age_band"), build = c.string("build_desc"), skinTone = c.string("skin_tone"), hair = c.string("hair"), eyes = c.string("eyes"), face = c.string("face"),
        distinctiveMarks = splitTokens(c.string("distinctive_marks")), speciesTokens = splitTokens(c.string("species_tokens")), bodyPlan = c.string("body_plan"),
        wardrobeTokens = splitTokens(c.string("wardrobe_tokens")), styleTokens = splitTokens(c.string("style_tokens")),
        canonicalFrameId = c.nullableString("canonical_frame_id"), updatedAtDay = c.int("updated_day"),
    )

    private fun galleryFrameFromCursor(c: android.database.Cursor) = GalleryFrame(
        id = c.string("id"), staffId = c.string("staff_id"), day = c.int("day"), role = GalleryFrameRole.valueOf(c.string("role")),
        localPath = c.string("local_path"), prompt = c.string("prompt"), negativePrompt = c.string("negative_prompt"), seed = c.nullableLong("seed"),
        width = c.int("width"), height = c.int("height"), referenceFrameId = c.nullableString("reference_frame_id"), profileRevision = c.int("profile_revision"),
        createdAtEpochMs = c.long("created_at"), canonical = c.int("is_canonical") != 0,
    )

    private fun splitTokens(raw: String): List<String> = raw.split('|').map(String::trim).filter(String::isNotBlank)

    private fun loadState(): GameState? {
        val db = readableDatabase
        val policy = db.query("establishment_policy", null, "id = 1", null, null, null, null).use { c ->
            if (c.moveToFirst()) PricingPolicy.valueOf(c.string("pricing_policy")) to WorkloadPolicy.valueOf(c.string("workload_policy"))
            else PricingPolicy.STANDARD to WorkloadPolicy.NORMAL
        }
        val row = db.query("game_state", null, "id = 1", null, null, null, null).use { cursor ->
            if (!cursor.moveToFirst()) return null
            GameState(
                schemaVersion = cursor.int("schema_version"),
                worldSeed = cursor.long("world_seed"),
                currentDay = cursor.int("current_day"),
                establishment = EstablishmentState(
                    id = cursor.string("establishment_id"), name = cursor.string("establishment_name"), level = cursor.int("establishment_level"),
                    treasury = cursor.long("treasury"), debt = cursor.long("debt"), publicReputation = cursor.int("public_reputation"), heat = cursor.int("heat"),
                    luxury = cursor.int("luxury"), secrecy = cursor.int("secrecy"), arcane = cursor.int("arcane"), politicalInfluence = cursor.int("political_influence"),
                    pricingPolicy = policy.first, workloadPolicy = policy.second,
                ),
            )
        }
        return row.copy(
            staff = loadStaff(db), quests = loadQuests(db), factions = loadFactions(db), artifacts = loadArtifacts(db), secrets = loadSecrets(db),
            staffRelations = loadStaffRelations(db), staffGoals = loadStaffGoals(db),
        )
    }

    private fun insertStaff(db: SQLiteDatabase, member: StaffMember) {
        db.insertOrThrow("staff", null, ContentValues().apply {
            put("id", member.id); put("name", member.name); put("species", member.species); put("age_years", member.ageYears)
            put("level", member.level); put("xp", member.xp); put("personal_money", member.personalMoney); put("loyalty", member.loyalty)
            put("stress", member.stress); put("fatigue", member.fatigue); put("health", member.health); put("reputation", member.reputation); put("status", member.status.name)
        })
        member.traits.forEach { trait -> db.insertOrThrow("staff_traits", null, ContentValues().apply { put("staff_id", member.id); put("trait", trait) }) }
        member.skills.values.forEach { skill -> db.insertOrThrow("staff_skills", null, ContentValues().apply { put("staff_id", member.id); put("code", skill.code); put("level", skill.level); put("xp", skill.xp) }) }
        member.preferences.forEach { (code, stance) -> db.insertOrThrow("staff_preferences", null, ContentValues().apply { put("staff_id", member.id); put("code", code); put("stance", stance.name) }) }
        member.inventory.forEach { item ->
            db.insertOrThrow("inventory", null, ContentValues().apply { put("item_id", item.id); put("staff_id", member.id); put("name", item.name); put("quantity", item.quantity) })
            item.tags.forEach { tag -> db.insertOrThrow("inventory_tags", null, ContentValues().apply { put("staff_id", member.id); put("item_id", item.id); put("tag", tag) }) }
        }
    }

    private fun loadStaff(db: SQLiteDatabase): List<StaffMember> = db.query("staff", null, null, null, null, null, "name").use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val id = cursor.string("id")
            val traits = stringColumn(db, "staff_traits", "trait", "staff_id", id)
            val skills = db.query("staff_skills", null, "staff_id = ?", arrayOf(id), null, null, "code").use { sc -> buildMap {
                while (sc.moveToNext()) { val code = sc.string("code"); put(code, SkillProgress(code, sc.int("level"), sc.int("xp"))) }
            }}
            val preferences = db.query("staff_preferences", null, "staff_id = ?", arrayOf(id), null, null, "code").use { pc -> buildMap {
                while (pc.moveToNext()) put(pc.string("code"), PreferenceStance.valueOf(pc.string("stance")))
            }}
            val inventory = db.query("inventory", null, "staff_id = ?", arrayOf(id), null, null, "name").use { ic -> buildList {
                while (ic.moveToNext()) {
                    val itemId = ic.string("item_id")
                    add(InventoryItem(itemId, ic.string("name"), ic.int("quantity"), stringColumn(db, "inventory_tags", "tag", "staff_id = ? AND item_id = ?", arrayOf(id, itemId))))
                }
            }}
            add(StaffMember(
                id = id, name = cursor.string("name"), species = cursor.string("species"), ageYears = cursor.int("age_years"), level = cursor.int("level"), xp = cursor.int("xp"),
                personalMoney = cursor.long("personal_money"), loyalty = cursor.int("loyalty"), stress = cursor.int("stress"), fatigue = cursor.int("fatigue"), health = cursor.int("health"),
                reputation = cursor.int("reputation"), status = StaffStatus.valueOf(cursor.string("status")), traits = traits, skills = skills, preferences = preferences, inventory = inventory,
            ))
        }
    }}

    private fun loadStaffRelations(db: SQLiteDatabase): List<StaffRelation> = db.query("staff_relations", null, null, null, null, null, "first_staff_id, second_staff_id").use { c -> buildList {
        while (c.moveToNext()) add(StaffRelation(c.string("first_staff_id"), c.string("second_staff_id"), c.int("affinity"), c.int("tension"), c.int("updated_day")))
    }}

    private fun loadStaffGoals(db: SQLiteDatabase): List<StaffGoal> = db.query("staff_goals", null, null, null, null, null, "created_day DESC, id").use { c -> buildList {
        while (c.moveToNext()) add(StaffGoal(
            id = c.string("id"), staffId = c.string("staff_id"), kind = StaffGoalKind.valueOf(c.string("kind")), title = c.string("title"),
            target = c.int("target"), progress = c.int("progress"), targetCode = c.nullableString("target_code"), createdDay = c.int("created_day"),
            deadlineDay = c.int("deadline_day"), status = StaffGoalStatus.valueOf(c.string("status")),
        ))
    }}

    private fun loadQuests(db: SQLiteDatabase): List<QuestState> = db.query("quests", null, null, null, null, null, "id").use { c -> buildList {
        while (c.moveToNext()) add(QuestState(c.string("id"), c.string("title"), c.string("summary"), QuestStatus.valueOf(c.string("status")), c.int("progress"), c.int("target")))
    }}
    private fun loadFactions(db: SQLiteDatabase): List<FactionState> = db.query("factions", null, null, null, null, null, "id").use { c -> buildList {
        while (c.moveToNext()) add(FactionState(c.string("id"), c.string("name"), c.int("relation"), c.int("influence"), c.int("attention")))
    }}
    private fun loadArtifacts(db: SQLiteDatabase): List<ArtifactState> = db.query("artifacts", null, null, null, null, null, "id").use { c -> buildList {
        while (c.moveToNext()) { val id = c.string("id"); add(ArtifactState(id, c.string("name"), c.int("charges"), c.int("max_charges"), stringColumn(db, "artifact_tags", "tag", "artifact_id", id))) }
    }}
    private fun loadSecrets(db: SQLiteDatabase): List<SecretState> = db.query("secrets", null, null, null, null, null, "id").use { c -> buildList {
        while (c.moveToNext()) add(SecretState(c.string("id"), c.string("title"), c.nullableString("owner_npc_id"), c.int("leverage"), c.int("exposed") != 0))
    }}

    private fun stringColumn(db: SQLiteDatabase, table: String, column: String, key: String, value: String): Set<String> = stringColumn(db, table, column, "$key = ?", arrayOf(value))
    private fun stringColumn(db: SQLiteDatabase, table: String, column: String, selection: String, args: Array<String>): Set<String> =
        db.query(table, arrayOf(column), selection, args, null, null, column).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun android.database.Cursor.index(name: String): Int = getColumnIndexOrThrow(name)
    private fun android.database.Cursor.string(name: String): String = getString(index(name))
    private fun android.database.Cursor.nullableString(name: String): String? = index(name).let { if (isNull(it)) null else getString(it) }
    private fun android.database.Cursor.int(name: String): Int = getInt(index(name))
    private fun android.database.Cursor.nullableInt(name: String): Int? = index(name).let { if (isNull(it)) null else getInt(it) }
    private fun android.database.Cursor.long(name: String): Long = getLong(index(name))
    private fun android.database.Cursor.nullableLong(name: String): Long? = index(name).let { if (isNull(it)) null else getLong(it) }
    private data class ReportHeader(val day: Int, val gross: Long, val upkeep: Long, val treasury: Long, val debt: Long, val createdAt: Long)

    companion object {
        private const val DATABASE_NAME = "game_broth.db"
        private const val DATABASE_VERSION = 6
    }
}
