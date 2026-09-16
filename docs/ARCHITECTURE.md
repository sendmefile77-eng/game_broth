# Game Broth architecture

## Save = truth

`player choice -> deterministic engine -> validated GameState -> SQLite -> compact facts -> local AI -> text/image presentation`

AI output never owns mechanics. Qwen and Local Dream receive finished facts; the simulation remains authoritative.

## Modules

- `core:model` — establishment, staff, daily plan status, skills, preferences/boundaries, inventory, reports, encounters, factions, quests, artifacts, secrets, memories/events.
- `core:simulation` — deterministic work/rest/training days, clients, progression, purchases and recruitment.
- `core:storage` — persistence interface.
- `core:ai-text` — Tellama/Qwen contracts + compact state digest.
- `core:ai-image` — Local Dream contracts, visual roles and prompt builder.
- `core:adult-contracts` — typed optional mature-content boundary plus non-graphic fallback.
- `app` — Compose UI, Android SQLite implementation, loopback HTTP clients and orchestration.

## Daily plan and day flow

The persisted `StaffStatus` is also the next-day player order:

- `AVAILABLE` / `WORKING` — work;
- `RESTING` — planned rest;
- `TRAINING` — planned training;
- `INJURED` — forced recovery;
- `LEFT` — absent from the active roster.

A plan change is written to SQLite immediately, so closing/reopening the app before the end of the day does not lose the order.

When the player closes the day:

1. `DayEngine` resolves every staff order deterministically from `worldSeed + day`.
2. Work creates adult client archetypes and one persisted `WorkEncounter` per visit.
3. `HARD_LIMIT` cannot be overridden; the encounter becomes a refusal instead.
4. Work calculates service outcome, money split, XP, fatigue/stress/health and incidents.
5. Rest restores fatigue/stress/health and forgoes revenue.
6. Training costs the establishment money and gives XP to the weakest skill.
7. Injured staff recover and cannot be assigned normal work from the UI.
8. Staff may spend only their own accumulated money on autonomous purchases.
9. Engine-generated diary memories and a structured `DailyReport` are persisted.
10. A factual local fallback chronicle is available immediately.
11. Qwen may replace the fallback with a richer prose chronicle but cannot change facts.
12. Local Dream renders one automatic `DAY_SCENE` from the most notable real staff result and Home switches to that image.

The day has already advanced before Qwen/Local Dream finish. Failure of either local model never rolls back mechanics.

## Local models

### Tellama / Qwen

Loopback Ollama-compatible server: `127.0.0.1:11434`.
The app uses `/api/tags` and streaming `/api/chat`.

The API key field is optional for the local server; Bearer auth is sent only when a key is configured.

Allowed: narration, dialogue/flavor, diaries and atmosphere based on supplied facts.
Forbidden: direct balance changes, save writes, invented numeric results or changing deterministic outcomes.

### Local Dream

Loopback image backend: `127.0.0.1:8081`.
`/health` checks readiness and `/generate` streams generated image data.

Current backend strategy is Illustrious/SDXL-oriented: concise positive tags, a separate negative prompt, DPM++ 2M scheduling and per-role step/CFG tuning. Image generation is serialized through a mutex so multiple requests do not fight for device memory.

## Persistence

SQLite DB version 3 persists:

- game/establishment state;
- staff including current daily plan in `status`;
- traits, skills, preferences and hard limits;
- inventory and tags;
- quests, factions, artifacts and secrets;
- append-only staff memories and world events;
- daily reports and per-staff summaries;
- individual client encounters;
- visual identity profiles;
- gallery metadata.

Gallery PNG files live in the app's private file storage and SQLite stores relative paths plus generation metadata.

## Visual identity pipeline

`StaffMember + VisualIdentityProfile + role + validated scene facts -> VisualPromptBuilder -> Local Dream -> GalleryFileStore + gallery_frames(SQLite)`

`VisualIdentityProfile` owns stable appearance only: face, hair, eyes, skin, build, species traits, body plan and permanent marks. It does **not** own pose, crop, viewpoint, background, lighting or scene composition.

Visual roles are separate:

- `RECRUIT_CARD` — full-body recruitment portrait;
- `STAFF_CARD` — full-body staff portrait suitable for manual canonical selection;
- `DAY_SCENE` — automatic erotic/non-graphic scene based on a completed day;
- `HOME_SCENE` — environment-first establishment scene reserved for broader world presentation.

Generated pixels never mutate identity automatically. No portrait becomes canonical by itself. The player may explicitly promote a portrait in the gallery.

The full canonical PNG is currently **not** passed into ordinary day scenes as generic img2img input. Earlier testing showed that this preserved the old pose/background/composition instead of only identity. Until Local Dream exposes a true identity-only adapter, stable text identity is preferred over composition-locked img2img.
