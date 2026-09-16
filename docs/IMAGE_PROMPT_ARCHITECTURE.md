# Image prompt architecture

The image pipeline separates **identity** from **visual role** and **scene composition**, and adapts the final request specifically for **Local Dream / WAI Illustrious SDXL v16**.

## Non-negotiable invariants

1. Every depicted staff/recruit character is an explicitly adult fictional woman.
2. Every generated game image carries an unmistakable adult erotic/sensual tone. There are no sterile neutral portraits in normal gameplay.
3. Erotic direction applies to the whole person and scene. It must not collapse into feet/legs/body-part isolation or fetish framing.
4. `VisualIdentityProfile` owns stable appearance only: face, hair, eyes, skin, build, species traits, permanent marks and body plan.
5. Identity never owns pose, crop, viewpoint, background, lighting, clothing arrangement or scene composition.
6. Full canonical/reference PNG is not used as generic img2img input for normal scenes because it locks composition as well as identity.
7. Scene prompts may change pose, location, framing, mood, wardrobe state and lighting without rewriting identity.
8. Local Dream receives concise comma-separated visual tags, not long prose instructions.
9. Words that Illustrious can interpret literally as layout objects (`card`, `frame`, `reference sheet`, `character sheet`) must never appear in positive prompts.

## Local Dream / Illustrious prompt format

Positive prompt structure is tag-based:

`1girl, solo, mature adult woman, adult body proportions, <identity>, <role tags>, <wardrobe>, <erotic tags>, <scene tags>, <style>`

Negative prompts explicitly block the failure modes seen in device tests:

- child/loli/chibi/young-looking proportions
- oversized anime eyes and doll-like child proportions
- ornamental borders, trading-card layouts, character/reference sheets
- generated text, letters, captions, logos and watermarks
- headshots/bust-only crops for full-body portraits
- feet/legs-only framing and body-part fetish framing
- camera equipment, photographers and tripods
- collage/panel layouts and duplicated people
- malformed anatomy

## Visual roles

### `RECRUIT_CARD`
Purpose: first erotic impression during recruitment.

- One adult fictional woman.
- Full body, head to both feet, readable face and normal adult proportions.
- Medium erotic tone: sensual posture, fitted/revealing non-explicit attire, flirtatious confidence.
- Simple dark-fantasy brothel-adjacent interior.
- No client and no narrative action.
- Positive prompt must never contain `card`, `frame`, `sheet`, or decorative-layout language.

### `STAFF_CARD`
Purpose: canonical erotic dossier image for a hired worker.

- One adult fictional woman.
- Full body, head to both feet, readable identity traits.
- High erotic tone: stronger seductive presentation, lingerie-inspired/fitted work attire where appropriate, confident sensual posture.
- No client and no story action.
- This image may be manually promoted to canonical identity reference.

### `DAY_SCENE`
Purpose: visual summary of how one worker lived through a completed workday.

- One adult fictional woman.
- High erotic tone integrated with the brothel environment and aftermath of the shift.
- The establishment interior is mandatory and visually important.
- Scene facts come from `DailyReport`/`StaffDayReport`: encounters, incident, purchase, earnings, fatigue, stress and health.
- Long report prose is not forwarded to Illustrious. The builder converts it into compact visual tags such as `visibly tired`, `tense aftermath`, `coins on table`, or `new personal item`.
- Not a neutral portrait and not an unrelated object study.

### `HOME_SCENE`
Purpose: current establishment/world atmosphere on the main screen.

- Environment-first dark-fantasy brothel scene.
- Medium erotic tone is mandatory in lighting, atmosphere, staff body language and styling.
- Must immediately read as an intimate operating establishment, not a generic fantasy tavern.

## Local Dream tuning

The Android Local Dream client chooses model-friendly settings by cache-key role:

- recruit portrait: DPM++ 2M, 20 steps, CFG 7.0
- staff portrait: DPM++ 2M, 20 steps, CFG 7.0
- day/event scene: DPM++ 2M, 24 steps, CFG 7.0
- home/story scene: DPM++ 2M, 22 steps, CFG 6.8
- batch remains one image at a time; recruitment portraits are generated sequentially

Cards are txt2img. If img2img is introduced later for a deliberate scene use-case, Local Dream's `denoise_strength` is treated literally and is no longer force-raised to 0.82.

## Erotic intensity

`MEDIUM` and `HIGH` are role-controlled. Erotic visual tags are always present.

- `MEDIUM`: clearly sensual, flirtatious, body-conscious styling, suggestive posture and intimate atmosphere.
- `HIGH`: stronger adult seductive presentation and intimate tension, while remaining non-graphic and compositionally coherent.

## Prompt assembly order

1. Quality/adult guards
2. Stable identity tags
3. Role/composition tags
4. Wardrobe tags
5. Erotic tags
6. Compact scene tags
7. Art-style tags

## Negative prompt policy

All roles block minors/ambiguous age, decorative card layouts, generated text, collage layouts, duplicated people, broken anatomy, photographic equipment and isolated body-part fetish framing.

Character portraits additionally block cropped head/feet, headless figures, bust-only crops, legs-only/feet-only framing, extreme perspective and action scenes.

Day/home scenes additionally block studio portraits, empty/abstract backdrops, product shots and generic tavern/inn drift.
