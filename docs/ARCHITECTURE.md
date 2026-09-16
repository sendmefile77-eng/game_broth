# Game Broth 1.0 architecture

## Save = truth

`player choice -> deterministic engine -> validated GameState -> SQLite -> compact facts -> local AI -> text/image presentation`

AI output never owns mechanics. Qwen and Local Dream receive finished facts; the simulation remains authoritative.

## Modules

- `core:model` — establishment, pricing/workload policy, staff, loyalty, relationships, goals, requests, skills, preferences/boundaries, inventory, reports, encounters and long-game state.
- `core:simulation` — `DayEngine`, `EstablishmentEngine`, `StaffLifeEngine`, `StaffSocialEngine`, recruitment, progression and deterministic consequences.
- `core:storage` — persistence interface.
- `core:ai-text` — Tellama/Qwen contracts + state digest containing staff status/goals/relationships and establishment policy.
- `core:ai-image` — Local Dream contracts, visual roles and prompt builder.
- `core:adult-contracts` — typed optional mature-content boundary plus non-graphic fallback.
- `app` — Compose UI, Android SQLite implementation, backup/archive layer, loopback HTTP clients and orchestration.

## Completed-day pipeline

The persisted `StaffStatus` is also the current player order:

- `AVAILABLE` / `WORKING` — work;
- `RESTING` — planned rest;
- `TRAINING` — planned training;
- `INJURED` — forced recovery;
- `LEFT` — preserved historical character outside the active roster.

When the player closes a day:

1. `DayEngine` resolves work/rest/training/recovery deterministically from `worldSeed + day`.
2. `HARD_LIMIT` cannot be overridden; an incompatible client request becomes a refusal.
3. Encounters calculate outcome, staff/business split, XP, fatigue/stress/health and incidents.
4. `EstablishmentEngine` applies pricing, workload, luxury, secrecy, heat, city-pressure costs and creditor interest to the completed day.
5. `StaffLifeEngine` changes loyalty, expires unanswered requests, may create new requests and can mark critically disloyal staff `LEFT`.
6. `StaffSocialEngine` updates affinity/tension, creates bond/conflict events, progresses personal goals and creates replacement goals.
7. The combined `GameState`, report, requests, memories and events are persisted.
8. A deterministic factual fallback chronicle is available immediately.
9. Qwen may replace that fallback with richer prose grounded in the same facts.
10. Local Dream creates one automatic `DAY_SCENE`; Home displays it as the completed-day hero image.

The day is committed before presentation AI runs. Qwen/Local Dream failure never rolls back gameplay.

## Establishment management

`EstablishmentEngine` owns management effects.

Pricing policy:

- `BUDGET` — lower business margin, modest reputation benefit;
- `STANDARD` — baseline;
- `PREMIUM` — higher business margin; luxury improves the value of this strategy.

Workload policy:

- `GENTLE` — lower margin, less fatigue/stress;
- `NORMAL` — baseline;
- `INTENSE` — higher margin, extra fatigue/stress and city attention.

Other mechanics:

- outstanding debt receives deterministic interest every third day;
- high `heat` creates operating costs;
- `luxury` increases revenue and passively relieves staff stress;
- `secrecy` shields/decays heat;
- the player may repay debt, upgrade luxury/secrecy or pay to `lay low`.

All decisions immediately save to SQLite and generate a world event.

## Living staff

`StaffLifeEngine` and `StaffSocialEngine` are deterministic game logic, not LLM agents.

Personal requests are persisted as `PENDING`, `ACCEPTED`, `REFUSED` or `EXPIRED`. Current request kinds are day off, training and bonus. Accepted rest/training promises cannot immediately be overwritten with a contradictory daily order.

`StaffRelation` stores normalized staff pairs with `affinity (-100..100)` and `tension (0..100)`. Relationship thresholds produce bond/conflict memories and high tension can increase stress.

Every active staff member has a persistent `StaffGoal`: earn personal money, improve a skill, recover health or build loyalty. Goals have progress and deadlines. Success/failure has mechanical consequences and remains in history.

## Local models

### Tellama / Qwen

Loopback Ollama-compatible server: `127.0.0.1:11434`. API key is optional for the local server. Qwen may narrate completed facts but cannot change balance, state or outcomes.

### Local Dream

Loopback image backend: `127.0.0.1:8081`. Prompts are Illustrious/SDXL-oriented concise tags with separate negative prompts and DPM++ 2M scheduling. Image inference is serialized by mutex.

Automatic day-scene facts include actual work/rest/training/recovery plus establishment luxury/secrecy/heat. Day scenes are erotic in atmosphere, non-graphic, and all participants are fictional adults.

## Persistence and backup

SQLite DB version **6** persists:

- establishment state and pricing/workload policy;
- staff, daily plan, loyalty and personal money;
- traits, skills, preferences, hard limits and inventory;
- staff requests and response history;
- pair relationships and personal goals/history;
- quests, factions, artifacts and secrets;
- memories/world events;
- daily reports, staff summaries and encounters;
- visual identity and gallery metadata.

Migrations are additive:

- v2 — reports/preferences/encounters;
- v3 — visual identity/gallery;
- v4 — staff requests;
- v5 — staff relations/goals;
- v6 — establishment pricing/workload policy.

`GameBackupStore` exports one ZIP-compatible backup containing the SQLite database plus the complete private `staff_gallery` directory. Import is staged and validates the manifest, safe paths and SQLite header before replacement. If installation fails, the current database/gallery are restored from rollback copies.

## Visual identity pipeline

`StaffMember + VisualIdentityProfile + role + validated scene facts -> VisualPromptBuilder -> Local Dream -> GalleryFileStore + gallery_frames(SQLite)`

`VisualIdentityProfile` owns stable appearance only. Pose, crop, viewpoint, background, lighting and scene composition never become identity facts automatically.

Visual roles:

- `RECRUIT_CARD` — full-body recruitment portrait;
- `STAFF_CARD` — full-body portrait suitable for manual canonical selection;
- `DAY_SCENE` — automatic erotic/non-graphic scene based on the completed day;
- `HOME_SCENE` — environment-first establishment scene reserved for later expansion.

No generated image becomes canonical automatically. Ordinary day scenes do not reuse the full canonical PNG as generic img2img input; text identity is preferred until Local Dream exposes a true identity-only adapter.
