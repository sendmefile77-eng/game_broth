# Game Broth architecture

## Save = truth

`player action -> deterministic engine -> validated GameState -> SQLite -> compact digest -> local AI -> text/image`

AI output is presentation or a typed proposal. It never gets a repository reference and never writes to SQLite.

## Modules

- `core:model` — establishment, staff, skills, preferences/boundaries, inventory, reports, client encounters, factions, quests, artifacts, secrets, memories/events.
- `core:simulation` — deterministic day, clients, progression, purchases and recruitment.
- `core:storage` — persistence interface.
- `core:ai-text` — Tellama/Qwen contracts + state digest.
- `core:ai-image` — Local Dream contracts.
- `core:adult-contracts` — typed optional mature-content boundary plus temporary non-graphic fallback.
- `app` — Compose UI, Android SQLite implementation, loopback HTTP clients and orchestration.
- `feature:adult` — future replaceable implementation, isolated from persistence.

## Day flow

1. The engine generates adult client archetypes from `worldSeed + day`.
2. Every client visit is stored as its own `WorkEncounter`.
3. A staff `HARD_LIMIT` cannot be overridden by the engine or model; the encounter becomes a refusal instead.
4. The engine calculates outcome, money split, XP, fatigue/stress/health changes and incidents.
5. Staff may spend only their own accumulated money on autonomous purchases.
6. A compact diary memory is appended from the deterministic facts.
7. A structured `DailyReport` is persisted.
8. If Tellama is available, Qwen receives the finished facts and may only rewrite them as prose.

## Local models

### Tellama / Qwen

Authenticated Ollama-compatible loopback server: `127.0.0.1:11434`.
The app uses `/api/tags` and streaming `/api/chat`.

Allowed: narration, NPC dialogue, diaries, rumors, flavor.
Forbidden by architecture: direct balance changes, save writes, invented numeric results.

### Local Dream

Loopback image backend: `127.0.0.1:8081`.
`/health` checks readiness and `/generate` streams generated image data.
Image generation never owns or blocks the simulation state.

## SQLite v2

Persistent tables cover:

- game/establishment state;
- staff, traits, skills, preferences and hard limits;
- inventory and tags;
- quests, factions, artifacts and secrets;
- append-only staff memories and world events;
- daily report headers;
- per-staff daily summaries;
- every individual client encounter.

Migration from the M0 v1 schema creates the new tables without deleting the existing save.


## Visual identity pipeline (M2)

`StaffMember -> VisualIdentityProfile(SQLite) -> VisualPromptBuilder -> LocalDream(reference PNG) -> GalleryFileStore + gallery_frames(SQLite)`

The visual profile is canonical game data. Generated pixels never mutate identity automatically. A frame can become canonical only through the explicit first-portrait rule or the `Make canonical` action. Old frames keep the profile revision they were generated from. Inventory items tagged `clothing`, `jewelry` or `visual` are injected as the current wardrobe layer.
