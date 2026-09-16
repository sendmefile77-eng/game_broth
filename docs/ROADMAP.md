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
- [x] Expenses, personal/business revenue split and debt
- [x] Daily report from engine facts + Qwen narration
- [x] Local fallback chronicle when Qwen is unavailable
- [x] Autonomous purchases from personal money
- [x] Fatigue/stress/injury/recovery mechanics
- [x] Diary memory from completed days
- [x] SQLite v1 -> v2 -> v3 -> v4 non-destructive migration
- [x] Staff daily plan UI: work / rest / training
- [x] Paid deterministic training of weakest skill
- [ ] Save export/import + backup

## M2 — living staff

Implemented in 0.7.0:

- [x] loyalty changes from actual day results and owner treatment
- [x] persistent personal requests: rest / training / bonus
- [x] accept, refuse and ignore/expiry consequences
- [x] accepted rest/training promises become real daily orders
- [x] low-loyalty warning in normal UI
- [x] deterministic resignation when loyalty collapses
- [x] request, loyalty and departure memories/events persisted locally

Next:

- [ ] personal long-term goals and fears
- [ ] explicit staff/player relationship history beyond one loyalty score
- [ ] staff-to-staff relationships, friendships, jealousy and conflicts
- [ ] negotiation/retention scene before some resignations
- [ ] relationships with recurring NPCs and clients
- [ ] richer long-term diary selection from memory
- [ ] complete progression screen with XP-to-next-level explanations
- [ ] former staff/history screen instead of only a departed count

## M3 — meaningful establishment management

- [ ] debt repayment controls and creditor pressure
- [ ] `heat` consequences plus player tools to reduce exposure
- [ ] luxury/secrecy upgrades with concrete mechanical effects
- [ ] room/capacity upgrades
- [ ] service focus / pricing / workload choices
- [ ] city locations with real persisted access costs and consequences
- [ ] establishment ledger/history screen

## M4 — city simulation

- [ ] factions, authorities, religious groups, guilds and rivals
- [ ] hidden reputation vector rather than one score
- [ ] client secrets/leverage and political quests
- [ ] recurring notable clients
- [ ] competitors, inspections/raids and expansion locations

## M5 — magic and long game

- [ ] artifacts with deterministic rules
- [ ] magical traits and inheritance
- [ ] branches and themed establishments
- [ ] city influence/endgame
- [ ] exportable chronology

## M6 — visual and presentation layer

- [x] separate recruitment/staff/day/home prompt roles
- [x] Illustrious/SDXL tag-oriented Local Dream prompts
- [x] persistent visual identity profiles
- [x] Local Dream gallery/cache metadata
- [x] sequential recruitment portrait inference
- [x] automatic day-scene generation from real report facts
- [x] main-screen day scene after closing the day
- [x] manual canonical portrait selection
- [x] serialized image inference
- [ ] stronger identity-only conditioning if Local Dream exposes a compatible adapter
- [ ] fullscreen gallery viewer and image deletion/regeneration
- [ ] dedicated establishment/home scenes independent of a single staff member
- [ ] subtle day-close transitions/animation

## Release hardening

- [ ] save backup/export + restore tests
- [ ] corrupt-save recovery path
- [ ] long-session / 30-day simulation test
- [ ] low-memory image-generation failure test
- [ ] offline/no-Qwen/no-Local-Dream UX pass
- [ ] accessibility and small-screen UI pass
