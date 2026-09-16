# Local text AI architecture

Game Broth keeps simulation authority outside every language model. Text AI receives compact facts and may only rewrite them into narrative.

## Backends

`TextBackendClient` is the routing facade used by game code. It can select either:

- `TELLAMA` — the existing localhost Ollama-compatible integration. This remains the default and fallback.
- `EMBEDDED` — an in-process `llama.cpp` backend using a user-imported GGUF model.

`TextBackendConfig` persists the selected backend, imports the GGUF from Android's document picker into app-private `files/models/text`, and stores a conservative mobile profile: 8k context, 6 threads, batch 256, and up to 1800 generated tokens.

No model weights belong in GitHub or the APK.

## Memory lifecycle

Both text and image generation share `LocalAiResourceGate`. Heavy jobs must never overlap.

Target end-of-day sequence:

1. deterministic simulation closes the day and stores the state;
2. text backend acquires the AI slot;
3. embedded text backend loads GGUF + KV cache, generates the chronicle, then unloads in `finally`;
4. image backend acquires the same slot;
5. diffusion loads its model, generates the day frame, then unloads;
6. UI returns to an idle state with neither heavy model resident.

Tellama already uses `keep_alive: 0`, so its Ollama-compatible server is asked to release the text model after each request.

## Prompt contract

`EmbeddedTextClient` assembles a strict prompt envelope:

- `SYSTEM` — narrator rules;
- `STATE` — compact authoritative game state;
- `RECENT_EVENTS` — bounded recent history;
- `FACTS` — completed-day facts;
- final rule forbidding invented mechanics, people, numbers, services, outcomes, or consequences.

This keeps the same trust boundary for Tellama and embedded models.

## Native pass

The Kotlin/JNI contract is already defined for a future `gamebroth_llama` library:

- `nativeRuntimeInfo()`
- `nativeLoadModel(path, contextTokens, threads, batchSize)`
- `nativeGenerate(prompt, maxTokens, temperature)`
- `nativeUnloadModel()`

The next implementation pass should link the pinned `third_party/llama.cpp` submodule through Android CMake/NDK and implement those four calls. Until then the embedded backend reports itself unavailable and the Tellama path remains untouched.
