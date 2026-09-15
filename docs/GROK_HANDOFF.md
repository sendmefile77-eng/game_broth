# Grok handoff: optional mature-content provider

The base project owns gameplay, persistence, UI, local AI clients and validation. Grok's future implementation belongs under `feature/adult` and must implement `AdultSceneProvider` from `core/adult-contracts`.

Rules:

1. Do not write to SQLite and do not mutate `GameState` directly.
2. Do not edit balance/storage schema without coordination.
3. Accept only `AdultSceneRequest` produced by the base game.
4. Contract input is adult-only and requires confirmed consent.
5. Respect staff preferences and hard limits supplied by the engine; never override them.
6. Return tags and `proposedEffects`; the base engine decides what is actually applied.
7. Text/image prompt logic owned by the optional provider stays isolated from persistence.
8. Base game must remain playable without `feature/adult`.
9. GitHub Actions remain manual only.

Until Grok is available, `FallbackAdultSceneProvider` supplies only non-graphic tags and bounded mechanical proposals so integration can be developed and tested now.
