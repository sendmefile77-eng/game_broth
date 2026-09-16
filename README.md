# Game Broth

Local-first Android dark-fantasy management game. The deterministic simulation and SQLite save are authoritative; local models turn finished game facts into prose and images but never own money, health, skills, boundaries, relationships or progression.

## 1.0 principles

- **Engine + SQLite are the source of truth.** Qwen and Local Dream cannot change mechanics.
- **Tellama/Qwen = narration.** It receives completed facts, staff state, goals, relationships and establishment policy and rewrites them as chronology and atmosphere.
- **Local Dream = images.** It renders sequential recruitment portraits, staff portraits and automatic day scenes from validated adult game facts.
- **Local-first.** Core gameplay, reports, requests, goals, relationships and fallback chronicle remain playable without either local model.
- **Manual GitHub Actions only.** CI remains `workflow_dispatch`; commits never start builds automatically.

## Playable loop — 1.0.0

`recruit -> answer staff requests -> set establishment policy -> choose daily plan -> work/rest/train -> close day -> economy + loyalty + relationships + goals -> report -> Qwen chronicle + Local Dream day scene -> next day`

Implemented:

- deterministic recruitment with three locations and three candidates;
- sequential Local Dream candidate portraits;
- daily staff orders: **work / rest / training**;
- adult client encounters, service outcomes and `HARD_LIMIT` enforcement;
- staff/business revenue split, upkeep, autonomous personal purchases and progression;
- fatigue, stress, health, injury and recovery;
- living loyalty, personal requests, accepted promises, refusals and resignation;
- persistent **staff relationships** with affinity/tension, bonds and conflicts;
- persistent **personal goals** with progress, deadlines, completion/failure and consequences;
- establishment **pricing**: budget / standard / premium;
- establishment **workload**: gentle / normal / intense;
- meaningful debt with periodic creditor interest;
- meaningful city attention (`heat`) with high-heat operating costs;
- luxury upgrades that improve economics and reduce staff stress;
- secrecy upgrades and a paid `lay low` action that reduce exposure;
- structured daily reports and deterministic local fallback chronicle;
- optional Qwen chronicle grounded in the same completed facts;
- automatic erotic/non-graphic Local Dream day image shown on Home after closing the day;
- day-image ambience reflects actual work/rest/training/recovery plus establishment luxury/secrecy/heat;
- persistent galleries and manual canonical portrait selection;
- full backup/export and restore archive containing SQLite + gallery PNG files;
- non-destructive SQLite migrations through **DB version 6**;
- deterministic 30-day simulation test for release hardening.

## Visual identity

Each staff member has a persistent `VisualIdentityProfile` with stable appearance facts. Pose, crop, lighting and scene composition remain separate. Local Dream uses role-specific Illustrious/SDXL-oriented prompts:

- `RECRUIT_CARD` — full-body erotic first impression;
- `STAFF_CARD` — full-body canonical portrait candidate;
- `DAY_SCENE` — automatic erotic/non-graphic scene based on the completed day;
- `HOME_SCENE` — environment-first establishment scene reserved for broader world presentation.

Generated images never become canonical automatically. A portrait becomes canonical only after explicit player choice. Ordinary day scenes do not feed the whole canonical PNG back through generic img2img because that previously locked pose/background together with identity.

See `docs/ARCHITECTURE.md`, `docs/IMAGE_PROMPT_ARCHITECTURE.md` and `docs/ROADMAP.md`.
