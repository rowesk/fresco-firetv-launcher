# Fresco: design

The home screen is a painting (or photograph) of the right time of day with almost nothing on it. Everything the launcher owns is a few pieces of liquid glass resting on one waterline: a small dock of your apps, a clock and a settings button. Focus is a bright **droplet** of milk glass that flows between them. Press Down and the whole sheet dips, like tilting your head toward the water, and your Continue watching posters surface. The glass has no colour of its own. The only hue the launcher ever adds is the **bedtime amber**, and it appears only where the tint is involved.

This document was written alongside an HTML prototype, which isn't included here; mentions of "the prototype" refer to it. The Android app in `app/` implements everything below.

Measurements are **px at 1920×1080**. On the stick 1 dp = 2 px, so divide by 2 for `dp`/`sp`.

---

## 1. Principles

- **The picture comes first.** A small dock instead of a full-width row of equal tiles, so the wallpaper keeps about 90 % of the screen.
- **Real glass on Android 9.** It's drawn from the wallpaper's own pre-blurred copy, magnified ×1.05 with a lensed rim, because Fire OS can't blur live.
- **Solved per wallpaper.** Tint, frost, focus brightness and scrims are calculated for each picture, so text contrast lands on target on bright and dark images alike (§6, §7).
- **Focus you can see from the sofa.** Focus is milk glass with dark ink, the brand colour waking on the icon, and a ×1.04 scale.
- **Light that follows the sun.** The rim highlight is top-left in the morning, overhead by day, top-right in the evening and faint at night.
- **A complete motion system.** The droplet, the dip, the reveal from black, the ambient settle, rolling clock digits and sliding values. All of it is transform and alpha only.

---

## 2. Canvas and layout

- **Title-safe**: 96 px left and right, 54 px top and bottom. Everything sits inside it.
- **One axis**: every left-aligned element starts at **x = 120**. That covers the dock, the peek line, the row title, the posters, the settings heading and the context column.
- **Right margin**: x = 1800, so 120 px from the right edge.
- **Waterline**: the dock sits low, at y = 712–944, leaving the sky and the subject of the photo clear.

### Home, at rest

| Element | x | y | w × h | Notes |
|---|---|---|---|---|
| Clock | right edge 1688 | 84 | – × 72 | 60 px Display 400, tabular figures |
| Gear (glass circle) | 1720 | 80 | 80 × 80 | Icon 36 px, stroke 1.6/24 |
| Dock (glass) | 120 | 712 | (216n + 16) × 232 for n = 1–4 apps (448 for two) | Radius 64 |
| Droplet (focus glass) | dock + 12 + 216 × app index | dock + 12 | 208 × 208 | Radius 52, which keeps the corners concentric: 64 − 12 |
| App cell | dock + 12 / + 228 | dock + 12 | 208 × 208 | Mark 96 px at (56, 34); label top 146 |
| Peek line | 120 | 968 | – × 32 | 24 px Text 500; chevron 24 px |

### Home, Continue watching open

The plane is one rigid sheet. It translates up by **400 px** and nothing reflows.

| Element | Screen position after the dip |
|---|---|
| Dock | leaves completely: alpha 0, scale 0.94 (pivot bottom-left). Up brings it back |
| Row title lane | x 96 (text at 120), y 594, 1300 × 64, clipped, soft left edge 24 px and right edge 40 px |
| Row meta lane | x 96 (text at 120), y 660, 1300 × 46, clipped, same edges |
| Posters | x 120 + i·204 (before the strip offset), y 736, 176 × 264, radius 22, gap 28 |
| Focused poster | lifted: translationY −8, scale 1.12 (pivot 50 % / 60 %), so about 718–1012 px tall, inside title-safe |

**Scroll model: focus is anchored.** The focused poster always sits at x = 120, directly under its title, and the strip slides beneath it: `strip.translationX = −i · 204`. Titles you have passed slip out behind the left edge (alpha → 0). The eye never has to travel: the title, the progress and the poster you're on stay in one place, and only the content changes. This is the Apple TV / Google TV pattern for a single row.

Style B (prototype key **V**) is the alternative for comparison. Focus moves freely, the strip scrolls only once focus passes the 5th poster (`−max(0, i − 4) · 204`), and the title rolls vertically instead of sliding.

### Launcher settings

| Element | x | y | w × h |
|---|---|---|---|
| Heading "Launcher settings" | 120 | 96 | 56 px Display 400 |
| Context column (section title, explanation, schedule graph) | 120 | 280 | 600 wide |
| Sheet (glass) | 800 | 72 | 1000 × 936, radius 48 |
| Section header | inset 48 | – | 24 px Text 600, ink 52 % |
| Row | inset 48 | – | 88 tall |
| Row droplet | inset 14 | follows row | 972 × 88, radius 34 |

### Toast

Top centre, y = 56. The pill is 100 px tall with radius 50 and padding 20 / 44. It holds a 64 px icon disc, a 30 px title and a 24 px subtitle.

---

## 3. Colour

| Token | Value | Use |
|---|---|---|
| `ink` | `#FFFFFF` | All text on photo and dark glass |
| `ink-2` | `#FFFFFF` @ 74 % | Secondary text on glass |
| `ink-3` | `#FFFFFF` @ 52 % | Section headers, graph ticks |
| `ink-dark` | `#10151B` | Text and icons on milk glass (focus) |
| `glass-dark` | `#090D12` @ α solved (0.10–0.78) | Glass tint |
| `frost` | `#FFFFFF` @ 0.04–0.10 | Glass lift; higher on dark photos so glass still reads as glass |
| `milk` | `#FFFFFF` @ solved (0.35–0.92) | Focus glass |
| `amber` | `#FFB25C` | Tint switch, strength fill, schedule graph, toast moon. **Nothing else.** |
| `tint-dim` | `#1F0C01` @ 0–0.58 | The tint overlay, default "Dim + amber" recipe (strength 0–100 %) |
| `amber-deep` | `#4A1E00` @ 0–0.55 | Alternative "Amber" tint recipe |
| `toast-bg` | `#18110C` @ 86 % | Smoked pill over other apps |
| `toast-ink` | `#FFF6EC`; subtitle `#FFECD6` @ 66 % | Toast text |
| Brand marks | Stremio gradient `#A57BFF → #5A3FE3`; YouTube `#FF0033`; any other app shows its own icon, muted (30 % saturation) at rest | Full colour only on the focused app; white mono marks (or the muted icon) otherwise |

---

## 4. Type

The app bundles two OFL fonts as variable TTFs, 98 KB each: `assets/fonts/RedHatDisplay-VF.ttf` and `RedHatText-VF.ttf`. Red Hat Display is drawn for large sizes and Red Hat Text for small ones; both are built for screens and have open apertures that survive 3 m. Display numerals are tabular (`tnum`), so the clock never jitters.

Android 9 can set the weight axis: `new Typeface.Builder(assets, "fonts/RedHatText-VF.ttf").setFontVariationSettings("'wght' 500").build()`. Build the five instances once at startup. For tabular figures use `paint.setFontFeatureSettings("tnum")`.

| Role | Face | px (sp) | Weight | Line height | Tracking |
|---|---|---|---|---|---|
| Clock | Display | 60 (30) | 400 | 72 | −0.01em |
| Settings heading | Display | 56 (28) | 400 | 64 | −0.01em |
| Row title / context title | Display | 44 / 40 | 500 | 52 / 48 | −0.005em |
| Settings row label and value | Text | 30 (15) | 500 / 400 | 40 | 0 |
| App label | Text | 28 (14) | 500 | 36 | 0 |
| Body (settings explanations) | Text | 28 | 400 | 42 | 0 |
| Meta (S/E · time left) | Text | 26 (13) | 500 | 34 | tnum |
| Peek / section header | Text | 24 (12) | 500 / 600 | 32 | 0 |
| Caption (small labels, graph ticks) | Text | 22 (11) | 500 | 30 | tnum |

Nothing is smaller than 22 px. On a 55" panel at 1080p, 22 px is about 14 mm.

**Static text shadows** (`TextView.setShadowLayer`, never animated):
- Clock: `0 2px 18px #000 @30 %`.
- Row title, meta and peek: `0 1px 2px #000 @45 %` plus a wide `0 0 18px #000 @40 %`. Android has one shadow layer per TextView, so use the wide one and draw the small one by stacking a second TextView, or accept the wide one alone.

---

## 5. Radii and spacing

- **Radii**: dock 64, droplet 52 (concentric), gear 40 (full circle), sheet 48, settings droplet 34, poster 22, toast 50, switch 21, strength track 5.
- **Spacing**:
  - Dock padding 12, cell gap 8.
  - Poster pitch 204.
  - Row title to meta gap 10; meta to posters 40.
  - Settings rows 88 tall; section header 30 above and 10 below.
  - More space always sits above a group than inside it.

---

## 6. Glass recipe

There is no live blur, and none is needed: the wallpaper is static, so a blur computed once is exact.

### 6.1 The blurred copy, made once per wallpaper

1. Decode the 1920×1080 WebP (ARGB_8888, 8.3 MB).
2. Area-downscale to **480×270** (¼). Go 1920 → 960 → 480 with `createScaledBitmap(…, true)` so it averages properly.
3. Run **3 passes of box blur, radius 6**, horizontal then vertical, in Java on the `int[]` pixels. This is roughly a Gaussian with σ ≈ 6.5 px at ¼ scale, or ≈ 26 px at full size, and costs a few ms.
4. Boost saturation ×1.30 with a `ColorMatrix` while drawing into the final bitmap. Liquid glass looks dull without it.
5. Average a **32×18 grid** of mean colours from it (60×60 px cells at full size) for the adaptation in §7.

Memory: 518 KB for the blurred copy, 2.3 KB for the grid.

`tools/prepare_wallpapers.py` runs this exact pipeline for the mocks.

### 6.2 Drawing a glass panel (`GlassView.onDraw`)

Draw these layers in order, each clipped to the panel's rounded rect by drawing *with* the shape. Use `drawRoundRect`, which is anti-aliased; don't use `clipPath`, which is aliased on HWUI.

1. **Shadow.** A static 9-patch behind the view, never animated:
   - Dock: offset y 18, blur 48, black 26 %, plus y 2 / blur 6 / 10 %.
   - Droplet y 10 / 26 / 20 %; gear y 10 / 28 / 22 %; sheet y 24 / 60 / 30 %; focused poster y 22 / 44 / 45 %.
   - The poster shadow fades in with focus (alpha only).
2. **Wallpaper.** `paint.setShader(new BitmapShader(blur, CLAMP, CLAMP))`. The local matrix maps bitmap pixels to the view, magnified by `m` about the panel's centre (the lens):
   ```java
   // x0,y0 = panel's top-left on screen; s = total scale (view × ancestors); W = width on screen
   float cx = x0 + W / 2f, cy = y0 + H / 2f;
   matrix.setScale(4f * m / s, 4f * m / s);
   matrix.postTranslate((cx * (1 - m) - x0) / s, (cy * (1 - m) - y0) / s);
   shader.setLocalMatrix(matrix);
   canvas.drawRoundRect(0, 0, w, h, r, r, paint);
   ```
   For the droplet's stretch, use separate `sx` and `sy` (the shader is counter-scaled per axis), so the glass content stays put while the shape stretches.

   `m` values: dock 1.05, droplet 1.10, gear 1.06, sheet 1.03, settings droplet 1.06, skeleton posters 1.04.
3. **Lens rim (dock only).** Draw a 12 px-wide stroke (inset 6) with the same shader at **m = 1.16** and paint alpha 0.70. Content at the edge is displaced further than in the middle, which reads as refraction through a thick edge.
4. **Tint.** Fill with `glass-dark` at α (solved), then white at `frost`.
5. **Sheen.** A vertical `LinearGradient`: white 15 % at 0 → 4 % at 34 % → 0 at 60 % → 5 % at 100 %. The settings sheet uses a softer sheen, because rows of text sit under its top edge: 8 % → 2 % at 30 % → 0 at 60 % → 4 %.
6. **Milk (focus glass only).** Fill with white at `milk` (solved).
7. **Glint (droplet only).** A `RadialGradient` ellipse, white 55 % → 0. Centre (8 % + 35 %, −30 % + 35 %) of the panel, 70 % × 70 % of its size, alpha 0.55.
8. **Specular rim.** A 2 px stroke (inset 1) with a `LinearGradient` along the period's light angle. Stops: white `rimHi` at 0, 10 % at 26 %, 3 % at 55 %, `rimLo` at 100 %.

| Period | Light angle (CSS gradient angle) | rimHi | rimLo |
|---|---|---|---|
| Morning 06–11 | 135° (from top-left) | 0.78 | 0.20 |
| Day 11–17 | 170° (from top, slightly left) | 0.72 | 0.18 |
| Evening 17–21 | 225° (from top-right) | 0.66 | 0.16 |
| Night 21–06 | 180° (from top) | 0.44 | 0.10 |

**When glass moves**, its shader must follow it. A glass view's `translationX/Y` and `scaleX/Y` don't re-run `onDraw`, so during an animation that moves glass, add an `AnimatorUpdateListener` that calls `glass.invalidate()`. Recompute the matrix in `onDraw` from `getLocationOnScreen()` and the accumulated scale. Only the moving glass views redraw: the dock (448×232), the droplet and the settings droplet. When idle, nothing invalidates.

The prototype does exactly this in `syncGlass()` (`app.js`), using `background-size` and `background-position` in place of the shader matrix. It runs only while an animation is in flight.

---

## 7. Adapting to bright and dark wallpapers

Glass alpha is **solved, not picked**. Solve against the faintest ink that sits on the glass. Text drawn at alpha *t* is the glass colour blended toward white by *t*, and the contrast is measured between that and the glass. When a wallpaper loads, look up the grid cells under each panel and take the **90th-percentile** colour, so a bright cloud behind the dock counts. Then binary-search (14 iterations) the parameter until the composite reaches its target. Composite in **sRGB space**, because that is how the GPU blends.

| Output | Composite searched | Target | Clamp |
|---|---|---|---|
| Dock tint α | p90 → mix `glass-dark` α → mix white `frost` | white text ≥ 4.5 : 1 **and** 64 %-white unfocused labels ≥ 3 : 1 | 0.10–0.78 |
| Gear tint α | same, gear cells | white icon ≥ 3 : 1 | 0.10–0.78 |
| Sheet tint α | same, against the **brightest** cell under the sheet, **unscrimmed**. The sheet's glass draws the raw blur, so the settings scrim is behind it, not under it; rows scroll anywhere. `frost` includes the sheet's 8 % sheen | 78 %-white values ≥ 4.5 : 1 and 62 %-white section headers ≥ 3 : 1 | 0.16–0.78 |
| `milk` (droplets) | p50 → mix white | luminance = 0.50, or **0.30 at night** (mean L < 0.07). The gear uses 0.60 / 0.36 because it is small | 0.35–0.92 |
| Top-right scrim | p90 → mix black | white ≥ 4.5 : 1 at the clock (gradient there is ~82 % of its peak) | 0.14–0.86 |
| Bottom scrim (peek) | p90 → mix black | **≥ 6 : 1** | 0.16–0.86 |
| Row scrim (title and meta) | p90 → mix black | **≥ 6 : 1** (gradient there is ~66 % of its peak) | 0.34–0.86 |
| Settings scrim | p90 of the context column → mix black | ≥ 4.5 : 1 | 0.22–0.86 |
| `frost` | from the whole photo's mean L | 0.10 − 0.06 · min(1, meanL / 0.25) | – |

Measured on rendered captures 13, 15 and 16 (the faintest unfocused text against the glass under it): section headers 4.1–4.5 : 1 (target 3), secondary values ("Connected · synced 21:46", "Skipped", times) at least 6.0 : 1 (target 4.5).

Text that sits straight on the **sharp** photo has higher targets (6 : 1). The grid measures the blurred copy, which under-reports bright detail such as leaves and sparkles on water. Text on glass sees only the blur, so 4.5 : 1 is exact there.

Computed values for the mock wallpapers (press **D** in the prototype to see them live):

| Wallpaper | mean L | frost | dock α | milk | top | bottom | row | settings |
|---|---|---|---|---|---|---|---|---|
| BB-173 misty lake (morning, very bright) | 0.45 | 0.04 | 0.48 | 0.35 | 0.36 | 0.55 | 0.75 | 0.50 |
| BB-191 alpine lake (day) | 0.26 | 0.04 | 0.10 | 0.62 | 0.49 | 0.50 | 0.34 | 0.42 |
| BB-201 pink sunset (evening) | 0.20 | 0.05 | 0.10 | 0.68 | 0.52 | 0.16 | 0.34 | 0.22 |
| BB-155 night lake | 0.02 | 0.10 | 0.10 | 0.49 | 0.14 | 0.16 | 0.34 | 0.22 |

Every style's pictures go through the same solve; nothing is tuned per set. `app/tools/check-glass.sh` runs the app's solver on the Mac over every packed wallpaper and flags any value at its clamp. The 28 paintings, busy as some are, all land inside the limits, in the same range as the photos (row scrim up to 0.82, settings scrim up to 0.44).

The scrims are static drawables: a radial gradient top-right, linear gradients from the bottom, and a flat colour for settings. Their alpha is set once per wallpaper; only the row scrim fades in during the dip.

### Scrim shapes

| Scrim | Shape |
|---|---|
| Top | Radial, 980 × 460 at the top-right corner. Stops: peak α at 0 %, 0.45 × peak at 42 %, transparent at 72 % |
| Bottom | Linear from the bottom. Stops: peak at 0 %, 0.5 × peak at 18 %, transparent at 44 % |
| Row | Linear from the bottom. Stops: peak at 0 %, 0.8 × at 30 %, 0.35 × at 52 %, transparent at 70 % |
| Settings | Flat `#04080C` at the solved α, laid over the full-screen blurred wallpaper |

---

## 8. Components

### App cell and droplet

- **Rest**:
  - White mono mark: Stremio is a rounded diamond with the play triangle cut out; YouTube is a rounded rect with the triangle cut out. Author them as `VectorDrawable`s.
  - Label is white at 64 %.
- **Focus**:
  - The droplet is under the cell.
  - The mono mark crossfades to the brand-colour mark (240 ms).
  - The label crossfades from white to `ink-dark`. Use two stacked TextViews with alpha only, never a colour animation.
  - The cell scales to 1.04.
- **Press (Select)**: the cell scales 1.04 → 0.95 → 1.04 over 200 ms, and the app launches at 110 ms.

### Gear

- **Rest**: dark glass circle with a white icon.
- **Focus**:
  - Scale 1.12.
  - Milk fades in; the icon turns `ink-dark`.
  - Without the old hold ring, this is the gear's whole focus state. It matches the dock droplet's language: a small milk-glass button.
- **Select**:
  - Opens **launcher settings** directly; there is no hold gesture.
  - The press dips the gear (1.12 → 1.02 → 1.12, 200 ms) and the cog turns 60° as settings open.
  - Amazon's own settings are a row inside launcher settings (see Settings rows).

### Clock

- `ACTION_TIME_TICK` updates it once a minute; 24-hour time.
- Only the digits that change animate: the old digit moves up 18 px and fades (220 ms, in-curve), the new one rises from 18 px (340 ms, out-curve, delay 60).
- Draw it as a small custom view (about 300 × 72) so the minute tick invalidates nothing else.

### Peek line

The line under the dock names what is below:

| State | Peek line |
|---|---|
| Ready | `⌄ The Simpsons · S17 E8` (title semibold) |
| Loading | `⌄ Continue watching` |
| Signed out | Text without the chevron, white 82 %, weight 400: "Sign in to Stremio on the Mac to see what you were watching" |
| Offline with nothing cached, or nothing played in the last 7 days | Absent |

### Poster

- 176 × 264, radius 22, with a 1.5 px inner edge at white 16 %.
- A bottom gradient (black 62 % → 0 over 34 %) carries the progress bar.
- **Progress bar**:
  - Inset 14 px; 6 px tall, radius 3.
  - Track white 30 %; fill white, drawn as a `scaleX` of a full-width bar (pivot left).
  - Time left comes from `state.duration − state.timeOffset` in the libraryItem.
- **View tree**. Each level owns exactly one transform, so the animations never fight:
  - `poster`: x nudge, alpha
    - `shadow`: alpha, translationY, scale. A 9-patch soft shadow (y 26 / blur 52 / 55 % plus y 8 / blur 18 / 25 %), inset 6 px.
    - `lift`: translationY, scale (pivot 50 % / 60 %)
      - `tilt`: rotationY. Set `setCameraDistance(8000 · density)`.
        - `img`: clips, radius 22. Contains `art`, the poster bitmap (translationX, scale), and `sweep`, the light band (translationX, alpha).
        - `edge`, `rimf`, `prog`
- **Focus is the poster itself lifting**, with no ring or outline:
  - It rises 8 px and scales to 1.12.
  - Its shadow deepens: it drops 18 px, grows to 1.06 and fades in.
  - A top-lit rim fades in: 2 px white at 55 %, masked from full at the top to 15 % at the bottom.
  - The artwork inside zooms to 1.05, so the frame and the picture separate slightly, which reads as depth.
- **Neighbours settle back**:
  - They stay **fully opaque**; nothing in the row is ever see-through.
  - They desaturate 15 % and dim 6 %. On Android, apply a `ColorMatrixColorFilter` to each poster's bitmap `Paint`: `ColorMatrix.setSaturation(0.85)` post-concatenated with a 0.94 scale on RGB.
  - A `ValueAnimator` animates the saturation and the dim with the focus change, using the same duration and curve as the lift. It invalidates only the two posters changing state, which are small views.
  - The two posters either side move 14 px away to make room for the lift.
- **Arrival**:
  - The poster turns to face you: rotationY from −10°·dir to 0, 640 ms.
  - Its artwork drifts in 12 px from the direction of travel and settles over 760 ms. That is parallax, and it is transform only.
- **Light sweep: removed 2026-11-08** at the user's request, after seeing it on the TV. The `sweep` view and its motion rows below are gone from the app.
- **Title and meta**:
  - Each lives in a fixed-height clipped lane, so a long title never moves anything.
  - On a move, the current text drifts **the way you're travelling** (−48 px·dir) and fades in 150 ms behind the lane's soft edge.
  - The new text then arrives **from the other side** (+56 px·dir → 0, 540 ms out-curve): title after 120 ms, meta after 190 ms.
  - The outgoing text is gone before the incoming text is visible, so the two never overlap.
- **Held D-pad**:
  - Moves are throttled to one every 120 ms.
  - Each one interrupts and retargets from wherever things are. The strip takes 200 ms, the lift 220 ms, and the text uses 80 ms out / 200 ms in.
  - The tilt, parallax and sweep are skipped until focus rests, so a long scroll stays crisp and never queues.
- **Android**:
  - Text layers are two TextViews per lane that swap roles each move.
  - Each lane is a FrameLayout with `clipChildren`. The soft edge is a horizontal fading edge (`setHorizontalFadingEdgeEnabled(true)`, length 24 px) or a pre-rendered gradient drawn with `DST_OUT` inside `withLayer()` during the move.

### Settings rows

| Row | Rest | Focused | Keys |
|---|---|---|---|
| Account | `● Connected · synced 21:46` / `○ Not signed in · sign in from the Mac` | dark ink on milk | Select = sync now ("Syncing…") |
| Wallpapers (own section "Home screen", after Stremio) | `Nature ›` (the current style) | dark ink on milk | Select **pushes the Wallpapers sub-page** |
| *Sub-page:* Nature / Art / Abstract / Space / Random | 112 × 63 thumbnail (radius 12) of what that style shows **right now**, the name, and a ✓ (32 px) on the current one. Random's thumbnail is a 2 × 2 mosaic of the other four, 2 px gaps | dark ink and ✓ on milk; the thumbnail is unchanged | Select **applies at once**: the ✓ moves and the wallpaper behind the sheet changes with the wallpaper-change transition. Selecting the current one presses its ✓ |
| Every evening (tint on a schedule) | Amber switch (on) | same, dark ink | Select toggles. **Off collapses the four rows below it** (see Collapsing the schedule) |
| Starts fading in / Full strength by / Turns off | `21:30` | `‹ 21:30 ›` | **Left/Right ∓/± 15 min**; held, after 6 repeats, steps become 30 min. Values slide out and in in the key's direction |
| Strength | `70%` plus a 300 × 10 amber track | `‹ 70% ━━━ ›` | Left/Right ∓/± 10 % (10–100). **While this row is focused, the real tint previews full-screen** |
| Skip the tint in | `5 apps ›` (or `4 of 5 apps ›`) | dark ink on milk | Select **pushes the sub-page**. This row stays visible when "Every evening" is off, because the Music button can still turn the tint on, and these apps must still be skipped |
| *Sub-page:* skipped app | name, "Skipped" / "Tinted", white switch | dark switch on milk | Select toggles skipped/tinted (knob and label in place, no re-render) |
| *Sub-page:* Add an app | `+ Add an app` | | Select pushes a third page of installed apps not yet listed; Select adds the app and pops back with it focused |
| Fire TV settings (own section "Fire TV", last in the sheet) | "Fire TV settings", small "Amazon's own", **↗ outward arrow** (30 px) | dark ink on milk | Select opens Amazon's Settings app; Back there returns to launcher settings with this row focused |

**Pages.** The main sheet, the "Wallpapers" and "Skip the tint in" sub-pages and the "Choose an app to skip" picker share one glass sheet. Both sub-pages push, pop and take focus the same way.
- The sub-page header carries a ‹ chevron to show that Back returns.
- Back always pops a page, with focus returning to the row that pushed it, and closes settings from the main page.
- **Push**: the current page slides out the way you're going (−72 px, alpha → 0, 170 ms, in-curve). The new page arrives from the other side (+104 px → 0, alpha 0 → 1, 440 ms, out-curve), and its first 9 rows follow with an extra 28 px offset staggered 24 ms. **Pop** is the mirror image.
- The left context column crossfades to the new page's section.
- Android: a container View per page, `translationX` + `alpha` on the container, and per-row `translationX` with `startDelay` for the stagger. Keys are ignored for the ~170 ms swap, so nothing queues.

**Scrolling.** The sheet is a fixed 936 px viewport and the content scrolls with `translationY` only.
- The focused row stays at least **150 px** inside the sheet's top and bottom edges. The content moves only when the next focus would leave that band, by exactly enough to keep it in, and it is clamped to the content's ends. So moving inside the band never scrolls, and the last row (Fire TV settings) can sit at the bottom.
- Scrolling takes 420 ms, std. When held, one row per 110 ms and 180 ms per scroll, each retargeting from the current position; the droplet also drops its stretch and moves in 150 ms.
- **Soft edges**: the sheet viewport masks content with a 56 px fade at the top and 72 px at the bottom, each present only while content is clipped beyond that edge. On Android, use `ScrollView` vertical fading edges (`setVerticalFadingEdgeEnabled`, `setFadingEdgeLength(64 px)`): they draw only on the side that has more content, and scrolling stays `smoothScrollTo`-free because the translation is ours.
- Scroll targets use the *logical* layout: where rows will be once any collapse in flight has committed. So scrolling during a collapse lands correctly.

**Collapsing the schedule.** "Every evening" off hides *Starts fading in, Full strength by, Turns off, Strength*; on brings them back. Focus stays on the toggle throughout.
- **Off**:
  - The knob travels (220 ms).
  - At the same time the four rows fold away bottom-up: alpha → 0, translationY −14, scaleY 0.94 from their top edge, 150 ms, (0.2, 0.6, 0.3, 1), 12 ms stagger.
  - From 110 ms, everything below (Skip the tint in, the Fire TV section) glides up 352 px as one sheet: 460 ms, (0.45, 0, 0.2, 1).
  - The rows have faded before the glide reaches them, so text never slides over visible text.
- **On**:
  - Everything below glides down 352 px to open the space: 480 ms, std, from 40 ms.
  - The four rows unfold into it top-down as it clears: alpha 0 → 1, translationY −14 → 0, scaleY 0.94 → 1, 340 ms, out-curve, from 170 ms with 50 ms stagger.
- **Reversing mid-way** retargets every row from its current position. A collapse only commits if it is still the latest request.
- **Layout changes exactly once**, at the end of a collapse: the rows go `GONE` and the translations reset to 0 in the same frame, which is visually identical. An expand does the inverse at its start: rows go `VISIBLE` with everything below pre-translated −352 px. No per-frame relayout.
  - Android: rows are plain views in a `LinearLayout` inside the `ScrollView`; animate `translationY`, `alpha` and `scaleY` with `ViewPropertyAnimator`; and commit `setVisibility` in `withEndAction` guarded by a generation counter.
- **Sheet height.** The glass sheet keeps its fixed 936 px height. Collapsed, the content simply ends higher inside it. Resizing the glass would mean redrawing a 1000 px-wide shader panel every frame for no real gain, so the sheet's rounded bottom stays put and the bottom edge fade switches off once nothing is clipped.
- **Graph.** The left column's schedule graph fades to 30 % and the text changes to "Off in the evenings. The Amazon Music button still turns it on for tonight."

**Switches** animate with transform and alpha only. The knob translates 34 px (220 ms, std). The on-track (white 90 %, or amber for the tint) and the on-knob are separate layers that crossfade over the off-track and off-knob.

**Row ink** flips from white to `ink-dark` when the settings droplet arrives under a row. That is a single redraw of that row, not an animation.

**Time pickers, designed for a remote**:
- No modal edit mode: Up and Down always move between rows, and Left and Right always change the value.
- Chevrons appear only on the focused row, telling you that left and right work.
- Limits:
  - Start and full stay within 17:00–02:00, and start is never after full. Pushing one past the other carries the other along, and that row's value bumps to show it.
  - Off stays within 04:00–12:00.
- Pushing at a limit bumps the value 10 px and back.

**Context column**: this follows the focused section.
- Stremio: the mark, the status and a sentence about background sync.
- Bedtime tint: a sentence, plus the **schedule graph**:
  - The axis runs 17:00 → 10:00. The amber area ramps from start to full, holds at the chosen strength and drops at off.
  - The focused time shows as a dashed marker with its value.
  - A "now" tick marks the current time.
  - On the stick this is a custom View; it redraws only when a value changes.
- Wallpapers: on the main row, that the picture follows the time of day; on the sub-page, the focused style (Art mentions the December set; Random that it holds until the next time of day). A style whose set isn't in the build says it shows Nature instead.
- Skip the tint in (the summary row and the whole sub-page): why copy-protected apps are skipped.
- Fire TV: what Amazon's settings cover, and that they open outside the launcher, with Back bringing you straight back.

The Fire TV row is the last section, so launcher settings open on the launcher's own rows (focus starts on Account). It sits in its own section with the outward arrow, so it's clear this row leaves the launcher.

### Tint overlay

- A full-screen `TYPE_ACCESSIBILITY_OVERLAY` view: a plain source-over colour layer. This is all the stick can do. The SurfaceFlinger colour matrix is blocked and Fire OS night display is disabled, both confirmed on the device, so there is no multiply and no true colour shift.
- **Default recipe, "Dim + amber"**: `#1F0C01` at alpha = **0.58 × strength**. Mostly dimming, with just a touch of amber. Blue light falls mainly because *all* light falls, which keeps haze and black-lift to a minimum. At 100 %:

  | Input | Output | Notes |
  |---|---|---|
  | Black | about rgb(18, 7, 1) | Within the rgb(18, 8, 2) budget |
  | White | about rgb(125, 114, 108) | Gently warm, not brown |
  | Contrast loss | – | Far smaller than with the amber recipe |

- **Alternative, "Amber"** (the previous recipe): `#4A1E00` at alpha = 0.55 × strength. At 100 %, black lifts to about rgb(41, 16, 0) and white becomes about rgb(155, 131, 115). It is warmer, but it leaves a visible brown veil over dark scenes.
- The strength setting scales alpha in both recipes. The prototype's key **T** switches recipes, and the active one is named in the key help. Compare them in `tint-compare-dim.png` / `tint-compare-amber.png` (over Stremio) and `tint-compare-home-dim.png` / `tint-compare-home-amber.png`.
- On schedule, alpha ramps linearly from start to full; at the off time it fades out over 2 s.
- Music button on → animate to full alpha over 900 ms. Music button off → 0 over 600 ms. These are one-shot full-screen alpha animations, cheap on this GPU.

### Toast (tint confirmation)

- **On**:
  - Amber moon (filled) in an amber 14 % disc.
  - "Bedtime tint on" / "Full strength until 07:00".
- **Off**:
  - Hollow moon in a white 8 % disc.
  - "Bedtime tint off" / "Back tomorrow from 21:30".
- Smoked pill `#18110C` at 86 %, with an inner top highlight 1.5 px `#FFECD6` at 16 % and an outline 1.5 px at 8 %.
- Drawn above the tint layer, in the same overlay window.
- Visible for about 2.4 s in total (§10).

---

## 9. Focus model and D-pad map

Focus is always on exactly one thing, and it is always shown by milk glass, a lifted poster or the settings droplet. A Home press lands on the **last-launched app**, defaulting to Stremio, with the row closed.

| Zone | Left | Right | Up | Down | Select | Back |
|---|---|---|---|---|---|---|
| Dock (1–4 apps) | prev app / bump | next app / bump | Gear | Open row (if ready or loading) / bump | Launch app | nothing (spec) |
| Gear | bump | bump | bump | Dock | Launcher settings | nothing |
| Row | prev poster / bump | next poster / bump | Dock (rise) | bump | Open `stremio:///detail/…` | Dock (rise) |
| Ambient | any key restores home and is consumed | | | | | |
| Settings | adjust − | adjust + | prev row / bump (scrolls to keep focus in the band) | next row / bump (same) | toggle / act / push a sub-page / open Fire TV settings | pop a page (focus returns to the row that opened it), else close to Gear |

**Bump** is the edge feedback: the focused thing moves 10 px toward the pressed direction and back, 240 ms. It is an additive transform, so it combines with focus scale.

Rewind on home (any zone) enters ambient. The Music button toggles the tint from anywhere. Home from anywhere runs the reveal (§10).

---

## 10. Motion

All motion is `translation`, `scale`, `rotation` or `alpha`. The one exception is shader-matrix updates on moving glass (§6.2). Every animation starts from the current visual state, so a new key press interrupts cleanly; `ViewPropertyAnimator` behaves this way by default.

### Easing tokens

| Token | cubic-bezier | Android | Use |
|---|---|---|---|
| `out` | (0.16, 1, 0.3, 1) | `new PathInterpolator(.16f, 1f, .3f, 1f)` | Arrivals and reveals |
| `std` | (0.2, 0.8, 0.2, 1) | `PathInterpolator(.2f, .8f, .2f, 1f)` | Focus, the dip, the sheet |
| `in` | (0.4, 0, 1, 1) | `PathInterpolator(.4f, 0f, 1f, 1f)` | Exits (always faster than entrances) |
| `drop` | (0.3, 0.9, 0.25, 1) | `PathInterpolator(.3f, .9f, .25f, 1f)` | The droplet |
| `develop` | (0.45, 0, 0.25, 1) | `PathInterpolator(.45f, 0f, .25f, 1f)` | Black veil lifting |
| `breathe` | (0.45, 0, 0.55, 1) | `PathInterpolator(.45f, 0f, .55f, 1f)` | Loading skeleton only |

### Every animation

Times are ms and delays are from the trigger. "→" means from the current value.

| Moment | Trigger | Element: property | Values | Duration | Delay / stagger | Easing |
|---|---|---|---|---|---|---|
| **Reveal from black** | Home press | Veil (launcher's own black view): alpha | 1 → 0 | 380 | 0 | develop |
| | | Wallpaper: scale | 1.04 → 1 | 480 | 0 | out |
| | | Clock: alpha | 0 → 1 | 260 | 60 | out |
| | | Gear: alpha, scale | 0 → 1, 0.9 → 1 | 300 | 90 | out |
| | | Dock: alpha, translationY | 0 → 1, 22 → 0 | 340 | 100 | out |
| | | Peek: alpha, translationY | 0 → 1, 12 → 0 | 300 | 160 | out |
| | | Droplet: alpha, scale | 0 → 1, 0.9 → 1 | 280 | 190 | out |
| | | *Total* | | **≈ 470** | | |
| **Droplet move** | Left/Right on the dock | Droplet: translationX | x₀ → x₀ + 0.55·Δ (at 42 %) → x₁ | 320 | – | drop (overall; keyframes linear) |
| | | Droplet: scaleX / scaleY | 1 → 1.13 / 0.96 (at 42 %) → 1 | 320 | – | drop |
| | | Mono mark / colour mark: alpha | 1 ↔ 0 | 200 / 240 | – | std |
| | | Label white / dark: alpha | 0.64 ↔ 0 / 0 ↔ 1 | 200 / 220 | – | std |
| | | Cell: scale | 1 ↔ 1.04 | 260 | – | std |
| **Focus leaves the dock** | Up to the gear, Down to the row | Droplet: alpha, scale | → 0, → 0.9 | 180 | – | in |
| **Focus enters the dock** | | Droplet: alpha, scale | → 1, → 1 | 260 | – | out |
| **Gear focus** | | Gear: scale; milk: alpha | 1 → 1.12; 0 → 1 | 240; 200 | – | std |
| **Gear select** | Select on the gear | Gear: scale | 1.12 → 1.02 → 1.12 | 200 | – | std |
| | | Cog icon: rotation | 0 → 60° | 420 | – | out |
| | | (opens settings) | | | 110 | |
| **Dip into the row** | Down | Plane: translationY | 0 → −400 | 460 | – | std |
| | | Dock: alpha, scale | 1 → 0, 1 → 0.94 | 300 | – | in |
| | | Peek: alpha | → 0 | 140 | – | in |
| | | Row: alpha | 0 → 1 | 260 | 80 | out |
| | | Row scrim / bottom scrim: alpha | 0 → 1 / → 0 | 460 / 300 | – | std |
| | | Posters (from the focused one rightwards): translationY | +36 → 0 | 420 | 100 + 34·i (first 7) | out |
| **Rise out of the row** | Up / Back | Plane: translationY | → 0 | 400 | – | std |
| | | Dock: alpha, scale | → 1, → 1 | 380 | – | std |
| | | Peek: alpha | → 1 | 260 | 160 | out |
| | | Row: alpha | → 0 | 200 | – | in |
| **Poster focus** (tap) | Left/Right in the row | Strip: translationX | → −i·204 (style B: −max(0, i−4)·204) | 540 | – | std |
| | | Focused `lift`: translationY, scale | → −8, → 1.12 | 480 | – | out |
| | | Focused `shadow`: alpha, translationY, scale | → 1, → 18, → 1.06 | 480 | – | out |
| | | Focused `rimf`: alpha | → 1 | 320 | – | out |
| | | Focused `tilt`: rotationY | −10°·dir → 0 | 640 | – | out |
| | | Focused `art`: translationX (scale 1.05) | 12·dir → 0 | 760 | – | out |
| | | Unfocused `lift`, `shadow`, `rimf` | → 0 / 1, → 0, → 0.94 | 340 | – | std |
| | | Others: saturation, brightness (ColorMatrix, ValueAnimator); neighbours: translationX | 1 → 0.85, 1 → 0.94 (focused: back to 1, 480 out); ±14 | 340 | – | std |
| | | Passed posters (style A only, leaving behind the left edge): alpha | → 0 | 260 | – | in |
| | | Focused `sweep`: translationX, alpha | −110 % → 250 %; 0 → 1 (20–75 %) → 0 | 1150 | 260 | (0.45, 0, 0.25, 1) |
| | | Title, meta out: translationX, alpha | → −48·dir, → 0 | 150 | – | in |
| | | Title in: translationX, alpha | 56·dir → 0, 0 → 1 | 540 | 120 | out |
| | | Meta in: translationX, alpha | 56·dir → 0, 0 → 1 | 540 | 190 | out |
| | | Style B text, instead of sliding: translationY | out → −24·dir; in 28·dir → 0 | 150 / 460 | 120 / 190 | in / out |
| **Poster focus** (held) | Key repeat, ≥ 120 ms apart | Strip | → target | 200 | – | (0.25, 0.6, 0.3, 1) |
| | | `lift` / `shadow` up; down | as above | 220; 160 | – | out; std |
| | | Text out / in | ∓16 / ±18 px | 80 / 200 | 0 / 60 | in / out |
| | | Tilt, parallax | skipped | – | – | – |
| | | Sweep | rescheduled; plays only once focus rests | 1150 | 260 | – |
| **Ambient in** | Rewind | Plane: alpha, translationY | → 0, +24 | 380 | – | (0.4, 0, 0.6, 1) |
| | | Gear: alpha, scale | → 0, → 0.9 | 260 | – | in |
| | | Bottom / row scrims: alpha | → 0 | 600 | – | std |
| | | **Clock: translationX** | 0 → +112 (settles into the gear's corner) | 720 | 140 | out |
| **Ambient out** | Any key | Clock: translationX | → 0 | 360 | – | std |
| | | Plane: alpha, translationY | → 1, → 0 | 380 | 60 | out |
| | | Gear: alpha, scale | → 1 | 300 | 80 | out |
| **Open settings** | Select on the gear | Full-screen blur layer: alpha | 0 → 1 | 340 | – | std |
| | | Home plane / clock / gear: alpha | → 0 | 220 / 200 | – | in |
| | | Settings root: alpha | 0 → 1 | 260 | 60 | out |
| | | Sheet: translationX, alpha | +48 → 0, 0 → 1 | 440 | 60 | out |
| | | Left column: translationX | −24 → 0 | 440 | 60 | out |
| | | Rows: translationX, alpha | +28 → 0, 0 → 1 | 360 | 140 + 20·i (first 10) | out |
| **Close settings** | Back | Settings: alpha; sheet: translationX | → 0; → +32 | 200; 220 | – | in |
| | | Blur layer: alpha | → 0 | 360 | 60 | std |
| | | Home plane / clock / gear | → 1 | 300–320 | 120 | out |
| **Settings droplet** | Up/Down | Droplet: translationY | y₀ → y₀ + 0.55·Δ (40 %) → y₁ | 300 | – | drop |
| | | Droplet: scaleX / scaleY | 1 → 0.985 / 1.12 (40 %) → 1 | 300 | – | drop |
| | | Sheet content: translationY | only when focus would leave the 150 px band; clamped to the content ends | 420 (held: 180) | – | std (held: (0.25, 0.6, 0.3, 1)) |
| | | Edge fades (top 56 / bottom 72 px) | on only while content is clipped on that side | 260 | – | std |
| **Settings droplet** (held) | Key repeat, ≥ 110 ms apart | Droplet: translationY | → y (no stretch) | 150 | – | (0.25, 0.6, 0.3, 1) |
| **Schedule off** | Select on "Every evening" | Knob: translationX; on-layers: alpha | 34 → 0; 1 → 0 | 220 | – | std |
| | | 4 schedule rows (bottom-up): alpha, translationY, scaleY | → 0, → −14, → 0.94 | 150 | 12·j | (0.2, 0.6, 0.3, 1) |
| | | Everything below: translationY | 0 → −352, then commit (GONE + reset) | 460 | 110 | (0.45, 0, 0.2, 1) |
| | | Graph: alpha | → 0.3 | 320 | – | std |
| **Schedule on** | Select on "Every evening" | Everything below: translationY | −352 → 0 (rows VISIBLE first) | 480 | 40 | std |
| | | 4 schedule rows (top-down): alpha, translationY, scaleY | 0 → 1, −14 → 0, 0.94 → 1 | 340 | 170 + 50·j | out |
| | | Context column (section change): out | alpha → 0 | 120 | – | in |
| | | Context column (section change): in | alpha 0 → 1, y +10 → 0 | 280 | 80 | out |
| **Value change** | Left/Right | Old value: translationX, alpha | 0 → −18·dir, → 0 | 120 | – | in |
| | | New value: translationX, alpha | 18·dir → 0, 0 → 1 | 200 | – | out |
| | | Strength fill: scaleX | → s/100 | 220 | – | std |
| **Strength preview** | Focus on/off the strength row | Tint overlay: alpha | → 0.58·s/100 (Amber: 0.55) / → its real state | 320 | – | std |
| **Style chosen** | Select on a Wallpapers choice | Old ✓: alpha | → 0 | 120 | – | in |
| | | New ✓: alpha, scale | 0 → 1, 0.6 → 1 | 280 | – | out |
| **Page push / pop** | Select "Wallpapers", "Skip the tint in" or "Add an app" / Back | Page out: translationX, alpha | → −72·dir, → 0 | 170 | – | in |
| | | Page in: translationX, alpha | 104·dir → 0, 0 → 1 | 440 | – | out |
| | | Rows in (first 9): extra translationX | 28·dir → 0 | 420 | 40 + 24·i | out |
| **Tint toast** | Music button | Tint overlay: alpha | → 0.58 (on; Amber 0.55) / → 0 (off) | 900 / 600 | 80 / 0 | std |
| | | Toast: alpha, translationY, scale | 0 → 1, −24 → 0, 0.96 → 1 | 360 | 0 | out |
| | | Moon: rotation | −40° (on) / +40° (off) → 0 | 560 | 40 | out |
| | | Toast exit: alpha, translationY | → 0, → −12 | 240 | 2160 | in |
| **Clock minute** | `TIME_TICK` | Changed digit: out | y → −18, alpha → 0 | 220 | – | in |
| | | Changed digit: in | y 18 → 0, alpha 0 → 1 | 340 | 60 | out |
| **Wallpaper change** | Period changes while home is visible, or a style is chosen in settings (the blur is already up, so it starts at the new blur) | Full-screen blur layer: alpha | 0 → 1 (defocus) | 600 | – | std |
| | | New blur layer + each glass panel's new shader: alpha | 0 → 1 | 700 | 620 | std |
| | | Blur layer: alpha (refocus onto the new photo) | 1 → 0 | 900 | 1440 | out |
| **Loading skeleton** | Row loading | Skeleton poster: alpha | 0.5 ↔ 0.9, alternating | 1400 | 120·i | breathe |
| **Edge bump** | Key at an edge | Focused element: translation (additive) | 0 → 10 → 0 | 240 | – | std |
| **Press** | Select on a cell or poster | Scale | s → s − 0.08 → s | 200 | – | std |

Stop the skeleton breathing after 8 s even if the network never answers. If the wallpaper change happens while home isn't visible, just swap the bitmaps.

**Reduced motion.** Fire OS has no system setting, so offer the toggle only if wanted. The prototype honours `prefers-reduced-motion`: translations and scales collapse to zero and opacity changes stay.

### How the reveal works on the stick

1. The accessibility service puts up its black cover and clicks the tile; that's existing behaviour.
2. In `onResume`, the launcher sets its own full-screen black **veil** view to alpha 1, resets home state and posts a frame callback.
3. On the first drawn frame (`ViewTreeObserver.OnDrawListener`, same process as the service), it tells the service to remove the cover, then starts the reveal.
4. The black hand-over is invisible, and the reveal never waits on another process.

---

## 11. States

| State | What you see | Notes |
|---|---|---|
| Home | Photo, clock, gear, dock with the droplet, peek | Idle: no redraw except the minute tick |
| Continue watching | Dipped plane, title and meta, posters | Row data comes from the on-stick cache; home never waits on the network |
| Row loading (first sync, nothing cached) | Peek says "Continue watching"; the row shows 6 breathing glass skeletons and two glass bars | Down allowed, Select inert |
| Signed out | Quiet peek line pointing to the Mac; row absent; Down bumps | Settings' account row says "Not signed in · sign in from the Mac" |
| Offline with nothing cached | No peek, no row, Down bumps | As if the feature didn't exist |
| Nothing played in the last 7 days | Same as offline: no peek, no row | The row lists only titles played in the last 7 days, most recent first (`state.lastWatched`, per show) |
| Ambient | Photo and clock only, settled into the top-right corner | Any key restores and is consumed. Scrims fade too, except the small top-right one behind the clock |
| Tint active | The whole screen is dim amber, launcher included | Scheduled ramp or Music button |
| Tint toast | Smoked pill top-centre, about 2.4 s | Over any app (see §12, item 3, for copy-protected apps) |
| Launcher settings | Blurred wallpaper, heading and context left, glass sheet right. Opened by Select on the gear | Back closes to the gear |
| Fire TV settings (Amazon's) | Amazon's app in front | Back returns to launcher settings, Fire TV row focused |
| Skip the tint in (sub-page) | Same sheet: ‹ header, the apps with switches, "+ Add an app" | Back returns to the main page with "Skip the tint in" focused |
| Picker | Same sheet, list replaced by "Choose an app to skip" | Back returns to "Add an app"; picking an app returns with that app focused |
| Schedule off | "Every evening" off; the 4 schedule rows hidden; graph at 30 % | The Music button still works; the skip list stays |
| Wallpaper change | Defocus, swap, refocus over about 2.3 s | At most 4 times a day, plus whenever a style is chosen. Choices made during a change are followed once it ends; settings closed mid-change stay on the blur until the new photo is up |

---

## 12. Conflicts and how they were resolved

1. **Glass on a moving panel.** The dock dips 400 px; a static shader would show the wrong patch of sky. Moving glass views redraw their shader each animation frame. Only these few small views do it, and only while they move, so idle stays redraw-free.
2. **Ambient keeps "only wallpaper and clock".** Nothing else is added. The clock glides 112 px into the corner the gear vacated, so the screen reads as finished rather than as missing pieces.
3. **The toast over copy-protected apps.** The toast is an overlay too, so it could trigger the same blackout the tint list exists to avoid. **When a skipped app is in front, the Music button toggles silently: no toast, no tint.** The toast can't be wallpaper glass over another app either; Android 9's accessibility service can't read other apps' pixels. So it is a self-contained smoked pill.
4. **"Blacks don't glow" versus visible warmth.** A source-over overlay can only blend toward its own colour. Anything warm enough to read as amber therefore lifts black and veils dark scenes; the earlier `#4A1E00` recipe lifted black to about rgb(41, 16, 0). A real colour shift would avoid this, but it was tested and is impossible on the stick (the SurfaceFlinger matrix is blocked and night display is disabled). So the default is now **"Dim + amber"**: `#1F0C01` at up to 0.58. It cuts blue mostly by cutting all light, and keeps black at about rgb(18, 7, 1) with whites gently warm. "Amber" stays available as the alternative. The strength row previews the real tint full-screen, so either recipe can be tuned by eye on the TV.
5. **One decoded wallpaper versus a crossfade.** A sharp-to-sharp crossfade would need 16 MB. The change goes through the small blurred copies instead: peak memory is one full bitmap plus two 518 KB copies.
6. **Signed-out row.** The spec lists signed out next to "row simply absent". The row *is* absent. One quiet line under the dock explains why, because otherwise there is no clue it exists. Offline-with-nothing-cached shows nothing at all.
7. **Back on home.** The spec says Back does nothing on home. In the row, Back rises to the dock, which still keeps you on home and never falls through to Amazon home.
8. **Two settings behind one gear.** The spec originally split them with tap (Fire TV) and hold (launcher). Holds are undiscoverable on a remote and needed a fill ring to explain themselves. Now Select always opens launcher settings, and Amazon's settings are one clearly marked row inside (↗, its own "Fire TV" section). That costs one extra press to reach Amazon's settings, and in exchange there is no hidden gesture.
9. **Text straight on photos.** The adaptation grid sees only the blur. Text over the sharp photo gets higher contrast targets (6 : 1) and a static shadow.
10. **Alpha on groups.** Fading the dock or plane, which have overlapping children, needs an offscreen layer. Use `animate().withLayer()` for those fades: a transient 448 × 232 hardware layer, released at the end.

---

## 13. Performance and memory

| Element | Memory | Draw cost | Idle |
|---|---|---|---|
| Wallpaper (1920×1080 ARGB_8888) | 8.3 MB | One bitmap quad | Static |
| Blurred copy 480×270 (+ second during a change) | 0.5 MB (+0.5) | Sampled by glass views and the settings layer | Static |
| Luminance grid 32×18 | 2 KB | – | – |
| Dock + droplet + gear (GlassView ×3) | Negligible | A few `drawRoundRect`s with shaders | Static |
| Shadows (9-patch PNGs) | ~40 KB total | One draw each | Static |
| Wallpapers sub-page thumbnails | 4 × 120×68 (decoded at `inSampleSize = 16`): ~130 KB, only while that page is up; released when it pops, settings close or the launcher leaves | Bitmap quads | Static |
| Posters (~10) | Decode `inSampleSize = 2` (250×375), scale to 208×312 (1.12 lift × 1.05 artwork zoom × 176×264, sharp when focused), keep only the scaled copy: ~260 KB each, **~2.6 MB** | Bitmap quads | Static |
| Fonts | 2 × 98 KB | – | – |
| Clock view | – | ~300×72 invalidate per minute | The only idle redraw |
| Tint overlay | – | One full-screen colour quad | Static |
| Transient hardware layers | ≤ 0.4 MB, only during group fades | – | Released |
| **Total images on screen** | **≈ 11–12 MB** | | |

When another app is in front, release the wallpaper, the blurred copy and the posters. That leaves the ~15 MB target for the accessibility service plus the tint overlay.

---
