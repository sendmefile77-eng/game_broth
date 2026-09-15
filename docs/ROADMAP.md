# Roadmap

## M0 — foundation

- [x] Multi-module Android skeleton
- [x] SQLite source of truth
- [x] Persistent staff stats, skills, inventory, memories and events
- [x] Deterministic day engine and recruitment
- [x] Tellama/Qwen + Local Dream loopback clients
- [x] Adult-module contract
- [x] Manual-only GitHub Actions

## M1 — playable first week

- [x] Adult client archetypes and deterministic demand
- [x] One persisted encounter per client
- [x] Staff preferences and hard limits stored as game data
- [x] Expenses, personal/business revenue split and debt pressure
- [x] Daily report from engine facts + optional Qwen narration
- [x] Autonomous purchases from personal money
- [x] Fatigue/stress/injury/recovery mechanics
- [x] Diary memory from completed days
- [x] SQLite v1 -> v2 non-destructive migration
- [ ] Staff assignment / work-rest schedule UI
- [ ] Save export/import + backup

## M2 — living staff

- relationships between staff/NPC/player;
- personal goals, fears and long-term mood;
- requests, conflicts, resignations and negotiation;
- complete level/skill progression screen;
- richer diary selection from long-term memory.

## M3 — city simulation

- factions, authorities, religious groups, guilds and rivals;
- hidden reputation vector rather than one score;
- client secrets/leverage and political quests;
- competitors, raids and expansion locations.

## M4 — magic and long game

- artifacts with deterministic rules;
- magical traits and inheritance;
- branches and themed establishments;
- city influence/endgame and exportable chronology.

## M5 — visual layer

- scene-image request planner;
- canonical character references;
- Local Dream cache/gallery;
- queued inference so text and image generation do not fight for device memory.
