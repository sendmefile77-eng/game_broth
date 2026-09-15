# Game Broth

Local-first Android fantasy management game built around the same separation that made Chronosphere stable: mechanics and memory live in code/SQLite, while local models only narrate or render validated facts.

## Architecture that must not be broken

- **Engine + SQLite are the source of truth.** Models do not own money, levels, health, inventory, boundaries, quests or history.
- **Tellama/Qwen = text.** It narrates daily reports and later NPC dialogue from a compact state digest.
- **Local Dream = images.** It is the separate on-device image backend.
- **Mature-content boundary is typed.** `core/adult-contracts` accepts adults with confirmed consent and returns bounded proposed effects. A temporary non-graphic fallback exists until the optional provider is replaced.
- **Persistent memory is local.** Staff stats, skills, preferences, hard limits, inventory, client encounters, diaries and world events survive app restarts.
- **GitHub Actions stay manual (`workflow_dispatch`) only.**

## M1 / 0.2.0

Implemented now:

- persistent establishment and staff state;
- staff skills up to level 15;
- persistent preferences and `HARD_LIMIT` boundaries;
- deterministic three-location / three-candidate recruitment;
- deterministic client generation and separate work encounters;
- individual staff/business revenue split;
- injuries, fatigue, stress, recovery and progression;
- autonomous purchases from personal money;
- diary memories generated from engine facts;
- structured daily reports persisted in SQLite;
- optional automatic Qwen narration of the report without permission to change facts;
- Tellama server client on `127.0.0.1:11434`;
- Local Dream client on `127.0.0.1:8081`;
- SQLite v1 -> v2 migration instead of destructive reset.

The base game remains playable if either local AI service is unavailable.


## M2 — visual identity and staff galleries

Each staff member now has a persistent `VisualIdentityProfile` stored in SQLite. Stable identity tokens (build, skin, hair, eyes, face, species traits, body plan and distinguishing marks) are injected into every Local Dream request. The current clothing/jewelry inventory is added as a separate visual layer, so purchases affect new frames without rewriting the base identity.

Every successful Local Dream result is saved under the staff member's private app gallery together with prompt, seed, dimensions, day, role, profile revision and reference-frame metadata. The first successful portrait becomes the default canonical reference; another frame can be promoted manually. Later generations use both the text identity and the canonical image reference.

SQLite DB version: 3. Existing M1 saves are migrated without deleting staff or history.
