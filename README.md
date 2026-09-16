# Game Broth

Local-first Android dark-fantasy management game. The simulation and save are authoritative; local models turn finished game facts into prose and images but do not own money, health, skills, boundaries or progression.

## Architecture that must not be broken

- **Engine + SQLite are the source of truth.** Models do not own money, levels, health, inventory, boundaries, quests or history.
- **Tellama/Qwen = narration.** It receives completed day facts and rewrites them as atmosphere and chronology. The local loopback server does not require an API key.
- **Local Dream = images.** It renders recruitment portraits, staff portraits and automatic day scenes from validated game facts.
- **Persistent memory is local.** Staff stats, daily plans, skills, preferences, hard limits, inventory, encounters, diaries, reports, visual identity and galleries survive restarts.
- **Manual GitHub Actions only.** CI remains `workflow_dispatch`; normal commits do not start builds automatically.

## Current playable loop — 0.6.0

`recruit -> choose daily plan -> work/rest/train -> close day -> deterministic report -> Qwen chronicle + Local Dream day scene -> next day`

Implemented:

- deterministic three-location / three-candidate recruitment;
- sequential Local Dream candidate portraits;
- persistent staff and establishment state;
- daily player orders: **work / rest / training**;
- deterministic adult client encounters and service outcomes;
- persisted preferences and `HARD_LIMIT` boundaries;
- staff/business revenue split, upkeep and debt;
- fatigue, stress, health, injury and recovery;
- skill and character progression;
- paid training that improves the weakest skill;
- autonomous purchases from personal money;
- factual diary memories and structured daily reports;
- immediate local fallback chronicle after every day;
- optional Qwen chronicle that replaces the fallback when available;
- automatic erotic/non-graphic Local Dream day image shown on the Home screen;
- day images based on real work/rest/training/recovery facts rather than generic scenes;
- persistent per-staff gallery;
- manual canonical portrait selection;
- SQLite migrations through DB version 3.

The simulation remains playable if Qwen or Local Dream is unavailable: mechanics, save, reports and the local fallback chronicle still work.

## Visual identity

Each staff member has a persistent `VisualIdentityProfile` containing stable appearance facts: build, skin, hair, eyes, face, species traits, body plan and distinguishing marks. Wardrobe/inventory is a separate visual layer.

Local Dream prompts are role-specific and tag-oriented for the current Illustrious/SDXL backend:

- `RECRUIT_CARD` — full-body first impression;
- `STAFF_CARD` — full-body canonical portrait candidate;
- `DAY_SCENE` — automatic scene based on the completed day;
- `HOME_SCENE` — environment-first establishment scene for future use.

Generated images never become canonical automatically. Only a portrait explicitly chosen by the player becomes the canonical gallery frame. Normal day scenes do not reuse the whole canonical PNG as generic img2img input, because that was found to lock pose/background/composition together with identity.

See `docs/IMAGE_PROMPT_ARCHITECTURE.md` for Local Dream prompt rules and `docs/ROADMAP.md` for the next gameplay milestones.
