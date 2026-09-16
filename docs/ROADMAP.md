# Roadmap

## 1.0 release scope

### Foundation and daily loop

- [x] Multi-module Android architecture
- [x] SQLite source of truth
- [x] Deterministic recruitment and day simulation
- [x] Staff work / rest / training plan
- [x] Adult client encounters with persisted `HARD_LIMIT` boundaries
- [x] Revenue split, upkeep, debt, progression, fatigue/stress/health and recovery
- [x] Persistent reports, encounters, diaries, memories and world events
- [x] Local deterministic fallback chronicle
- [x] Optional grounded Qwen narration
- [x] Manual-only GitHub Actions

### Living staff

- [x] loyalty from actual results and owner treatment
- [x] personal requests: rest / training / bonus
- [x] accept/refuse/expiry consequences
- [x] promised rest/training locked into daily plan
- [x] resignation when loyalty collapses
- [x] persistent staff-to-staff affinity/tension
- [x] deterministic bond/conflict events
- [x] personal long-term goals with progress/deadlines
- [x] goal completion/failure consequences and history
- [x] goals and relationships visible in staff dossier and Qwen state digest

### Establishment management

- [x] pricing policy: budget / standard / premium
- [x] workload policy: gentle / normal / intense
- [x] debt repayment control
- [x] periodic creditor interest
- [x] high-heat operating costs
- [x] secrecy shield and paid `lay low`
- [x] luxury upgrades with revenue/stress effects
- [x] secrecy upgrades with heat effects
- [x] management state persisted in SQLite v6

### Visual layer

- [x] separate recruitment/staff/day/home prompt roles
- [x] Illustrious/SDXL tag-oriented Local Dream prompts
- [x] persistent visual identity profiles
- [x] sequential recruitment portrait inference
- [x] automatic erotic/non-graphic day scene from real completed-day facts
- [x] day scene displayed on Home after closing day
- [x] establishment luxury/secrecy/heat reflected in day-scene ambience
- [x] manual canonical portrait selection
- [x] serialized image inference

### Release hardening

- [x] non-destructive SQLite migrations v1 -> v6
- [x] full save backup/export: SQLite + gallery files
- [x] staged restore with archive/path/SQLite validation and rollback on install failure
- [x] deterministic 30-day simulation test
- [x] Qwen digest regression tests
- [x] image prompt regression tests
- [x] safe-area UI padding for Android system bars
- [x] gameplay remains functional without Qwen or Local Dream
- [ ] first real 1.0 Android CI/build run — intentionally postponed until code freeze

## After 1.0

These are expansion work, not blockers for the first release:

- [ ] negotiation/retention scene before some resignations
- [ ] relationships with recurring named clients/NPCs
- [ ] richer long-term diary/history browser
- [ ] former-staff history screen
- [ ] full establishment ledger/history screen
- [ ] room/capacity upgrades
- [ ] city locations with persisted access costs and consequences
- [ ] factions, authorities, religious groups, guilds and rivals
- [ ] recurring notable clients and secrets/leverage
- [ ] inspections/raids and competitors
- [ ] artifacts, magical traits and inheritance
- [ ] branches/themed establishments and city endgame
- [ ] exportable full chronology
- [ ] stronger identity-only visual conditioning when Local Dream exposes a compatible adapter
- [ ] fullscreen gallery viewer with delete/regenerate
- [ ] dedicated establishment-wide Home scenes
- [ ] day-close transitions/animation
- [ ] dedicated corrupt-database recovery UI beyond validated backup restore
- [ ] automated low-memory image-generation stress test
- [ ] broader accessibility/small-device layout QA after first device feedback
