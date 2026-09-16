# Image prompt architecture

The image pipeline separates **identity** from **visual role** and **scene composition**.

## Non-negotiable invariants

1. Every depicted staff/recruit character is an explicitly adult fictional woman.
2. Every generated game image carries an unmistakable adult erotic/sensual tone. There are no sterile neutral portraits in normal gameplay.
3. Erotic direction applies to the whole person and scene. It must not collapse into feet/legs/body-part isolation or fetish framing.
4. `VisualIdentityProfile` owns stable appearance only: face, hair, eyes, skin, build, species traits, permanent marks and body plan.
5. Identity never owns pose, crop, viewpoint, background, lighting, clothing arrangement or scene composition.
6. Full canonical/reference PNG is not used as generic img2img input for normal scenes because it locks composition as well as identity.
7. Scene prompts may change pose, location, framing, mood, wardrobe state and lighting without rewriting identity.

## Visual roles

### `RECRUIT_CARD`
Purpose: first erotic impression during recruitment.

- One adult fictional woman.
- Full body, head to both feet, readable face and normal proportions.
- Medium erotic tone: sensual posture, body-conscious/revealing attire, flirtatious confidence.
- Clean presentation with simple dark-fantasy recruitment/tavern/brothel-adjacent background.
- No client and no narrative action.

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
- Not a neutral portrait and not an unrelated object study.

### `HOME_SCENE`
Purpose: current establishment/world atmosphere on the main screen.

- Environment-first dark-fantasy brothel scene.
- Medium erotic tone is mandatory in lighting, atmosphere, staff body language and styling.
- Must immediately read as an intimate operating establishment, not a generic fantasy tavern.

## Erotic intensity

`MEDIUM` and `HIGH` are role-controlled. The erotic block is always present.

- `MEDIUM`: clearly sensual, flirtatious, body-conscious styling, suggestive posture and intimate atmosphere.
- `HIGH`: stronger adult seductive presentation and intimate tension, while remaining non-graphic and compositionally coherent.

## Prompt assembly order

1. Role block
2. Identity anchor
3. Wardrobe state
4. Mandatory erotic core
5. Scene direction/facts
6. Art style
7. Reference-image rule
8. Anatomy/consistency guard

## Negative prompt policy

All roles block minors/ambiguous age, collage layouts, duplicated people, broken anatomy, photographic equipment and isolated body-part fetish framing.

Character cards additionally block cropped head/feet, headless figures, legs-only/feet-only framing, extreme perspective and action scenes.

Day/home scenes additionally block studio portraits, character sheets, empty/abstract backdrops, product shots and generic tavern/inn drift.
