# Fresco: design

The home screen is a painting (or photograph) of the right time of day with almost nothing on it. Everything the launcher owns is a few pieces of liquid glass resting on one waterline: a small dock of your apps, a clock and a settings button. Focus is a bright **droplet** of milk glass that flows between them. Press Down and the whole sheet dips, like tilting your head toward the water, and your Continue watching posters surface. The glass has no colour of its own. The only hue the launcher ever adds is the **bedtime amber**, and it appears only where the tint is involved.

This describes the shipped 1.2 design. It grew alongside an HTML prototype, which is not included here; the Java in `app/` is the source of truth.

Measurements are **px at 1920×1080**. On the stick 1 dp = 2 px, so divide by 2 for `dp`/`sp`.

---

## 1. Principles

- **The picture comes first.** A small dock instead of a full-width row of equal tiles, so the wallpaper keeps about 90 % of the screen.
- **Real glass on Android 9.** It's drawn from the wallpaper's own pre-blurred copy, magnified ×1.05 with a lensed rim, because Fire OS can't blur live.
- **Solved per wallpaper.** Glass tint, frost, focus brightness and picture ink are calculated for each wallpaper (§6, §7). Bottom text gets a gradient only where neither ink holds; the clock stays plain on every picture.
- **Focus you can see from the sofa.** Focus is milk glass with dark ink, the brand colour waking on the mark, and a ×1.04 scale.
- **Light that follows the sun.** The rim highlight is top-left in the morning, overhead by day, top-right in the evening and faint at night.
- **A complete motion system.** The droplet, the dip, the reveal from black, the ambient settle, rolling clock digits and sliding values. Most of it is transform and alpha; the few drawing exceptions are in §10.

---

## 2. Canvas and layout

- **Frame**: x **120–1800**, y **80–1000**. Left-aligned ink starts on x 120, tops on y 80 and bottoms on y 1000. A hanging Back chevron and the text lanes' fade margins sit outside that axis.
- **Waterline**: the dock sits at y **712–944**, leaving the sky and the subject clear.
- **One plane**: the dock, peek and Continue watching row move together; the wallpaper, clock and gear stay on the stage.

### Home, at rest

| Element | Geometry | Notes |
|---|---|---|
| Wallpaper | 1920 × 1080, sharp | Its 480 × 270 blurred copy feeds the glass |
| Clock | Right edge **1688**; 72 px view at y 84, cap-height centred on **y 120** | Four 36 px tabular digit cells and a 16.8 px colon; 60/400; colon 86 %, drawn 4 px higher. Light or dark ink, **no shadow or backing** |
| Gear | **1720–1800 × 80–160**, radius 40 | 36 px icon; focus scales to 1.12, milk and dark cog |
| Dock | **x 120, y 712**, height 232, width **216n + 16**, n = 1–4 (448 for two) | Radius 64; 12 px lensed rim at ×1.16 |
| Cells | 208 × 208, dock + 12, pitch 216 (8 px gap) | 96 px mark at (56, 30); label baseline 160, 26/500, 78 %, in a centred 192 px box |
| Droplet | 208 × 208, radius 52 | Milk and glint; focused cell scales 1.04; brand mark and dark label |
| Peek | **x 120, line box 968–1000**, baseline 994 | Title 26/600 at 100 %, episode 26/500 at 86 %; 5 px dot with 12 px either side; 24 px chevron 8 px after the text, centred on the cap height |

### Home, Continue watching open

The plane translates up **400 px**; nothing reflows. The dock fades to alpha 0 and scale .94 from its bottom-left corner.

| Element | Screen position after the dip |
|---|---|
| Title | **569–621**, baseline 611, 44/500 at 100 %; ink on x 120; lane x 96–1396 with 24 px soft edges |
| Meta | **629–661**, baseline 655, 26/500 at 86 %; e.g. “S3 E8 · 22 min left” |
| Posters | **y 728–992**, 176 × 264, radius 22, pitch 204 (28 px gap) |
| Focused poster | Scale **1.125** from the top-left, translationY **−25**: **198 × 297 at x 120, y 703–1000** |
| Other posters | Everything right of focus moves +22 px, keeping each visible gap at 28; passed posters fade to 0 |

**Focus is anchored.** The strip slides beneath the title: `translationX = −i · 204`. The title, episode details and focused poster stay on one axis. The title and meta use one stable ink solution over the widest strings in the row, so moving focus never flips their ink.

### Launcher settings

| Element | Geometry |
|---|---|
| Backdrop | Full-screen blurred wallpaper with the solved settings scrim (§7) |
| Sheet | **x 800–1800, y 80**, radius 48; content height + 12 px at each end, bottom ≤ 1000 |
| Rows | **88 px** high; Wallpapers rows **148 px**; group gaps **32 px** |
| Labels and leads | Labels inset 48 (x 848); a 48 px app mark, 32 px plus or **208 × 116** thumbnail precedes the label by 24 px |
| Values and trailing glyphs | Text ends at **1712**; one **24 px** trailing slot at **1728–1752**; switches **76 × 42** end at 1752. Text is centred by cap height |
| Row droplet | Inset **12**, width **976**, row height, radius **36**; ×1.10 refraction, solved milk, top light, bottom shade and soft shadow |
| Steppers | Only focus shows ‹ and ›; ‹ ends 16 px before the value's first ink, › takes the trailing slot. Strength track **320 × 10**, radius 5, ends 16 px before ‹ |
| Left column | 600 px measure at x 120; eyebrow cap top **80**, hanging ‹ at x 96; title cap top **24 px below the eyebrow baseline**; **48** to item title or paragraph; item title 32/500 then **16** to paragraph ascenders; paragraph 26/38, ≤ 3 balanced lines; **32** to graph or dock preview |
| Dock preview | 64 px cells, 8 px padding and gaps, radii 24 / 16, marks 48; dock glass with a 4 px lensed rim. Focused candidate uses milk and brand colour |
| Graph | **600 px** wide; a permanent top band for the 26/500 focused time, baseline 12 px above its dashed marker; label clamped inside x 120–720; all six 22/500 tick labels stay visible; axis **17:11–10:11**, no top rule |

Long pickers use a whole-row window, with a 56 px top fade once scrolled and a 72 px bottom fade while clipped. The button picker has **824 px of row content inside an 848 px sheet/viewport**, with 12 px padding at each end; the 32 px group gap is part of the row content. The page model and whole-row fit determine the height. Details are in §8.

### Toast

A **96 px** pill at **y 80**, centred on x 960, radius **48**. The 64 px disc sits 16 px from the left, with a 32 px moon; text starts 24 px after the disc and has 32 px right padding. Title 32/500 and subtitle 26/400 are centred as one ink block, with the subtitle's cap top 12 px below the title's baseline. The smoked fill is `#18110C` at 86 %, with a 1.5 px inner rim.

---

## 3. Colour

| Surface | Level 1 | Level 2 | Level 3 |
|---|---|---|---|
| Glass (dock, sheet) | White 100 % | White **78 %** | White **62 %**, dimmed rows only |
| Bare picture (clock, peek, row) | White or **#10151B**, 100 % | Same ink **86 %** | – |
| Settings left column, on its scrim | White 100 % | White 86 % | – |
| Milk (focused droplets) | **#10151B**, 100 % | #10151B **70 %** | – |
| Toast | **#FFF6EC** | **#FFECD6 at 78 %** | – |

| Fill | Value | Use |
|---|---|---|
| Glass tint | `#090D12`, solved alpha .10–.78 | Darkening the glass |
| Frost | White .04–.10 | More lift on dark pictures |
| Milk | White, solved .35–.92; row floors in §7 | Focus |
| Track | Ink at **24 %** | Switch off, strength track, poster progress, graph axis |
| Amber | **#FFB25C** on glass, **#C26A12** on milk | Only bedtime tint: switch, strength, graph and toast moon |
| Tint overlay | **#1F0C01**, alpha 0–.58 × strength | Dim + amber |
| Toast background | **#18110C at 86 %** | Smoked pill over other apps |

Brand marks wake in colour only on focus: Stremio `#A57BFF → #5A3FE3`, YouTube `#FF0033`; other known apps use `MarkArt` (dark ink for brands too pale or dark for milk). Unknown apps get a monogram coloured from their icon, clamped to luminance .06–.20. Resting marks are white; the app's own icon is never shown.

---

## 4. Type

Two bundled OFL variable fonts, Red Hat Display and Red Hat Text, cover five roles. The five weight instances are built once with `Typeface.Builder` and the weight axis. Every run containing numerals uses tabular figures (`tnum`).

| Role | Face / weight | Size / line (px) | Tracking | Where |
|---|---|---|---|---|
| Display | Display 400 | **60 / 72** | **−1 px** | Clock, settings page title |
| Title | Display 500 | **44 / 52** | 0 | Continue watching title only |
| Row | Text 500 label, 400 value | **32 / 40** | 0 | Settings rows, left-column item title, toast title |
| Body | Text 400; 500 meta, peek and dock labels; 600 eyebrow and peek title | **26 / 32**, paragraphs **26 / 38** | 0 | Explanations and supporting text |
| Caption | Text 500 | **22 / 28** | 0 | Graph ticks |

Text is placed by **baseline**, not its box. Display lines, eyebrows and paragraphs are pulled left by the first glyph's side bearing (`getTextBounds`) so their ink shares x 120. Paragraphs use balanced breaking, no hyphenation, a 600 px measure and at most three lines.

The clock, peek, row title and meta have **no text shadows** in either ink. Their light and dark views crossfade by alpha. The dock's resting white label retains its small static shadow on glass.

---

## 5. Radii and spacing

- **Spacing scale**: **8, 12, 16, 24, 32, 48, 80, 120**. Settings group gaps are 32. Concentric insets, the dot separator and the dock cell's optical placement are the exceptions.
- **Concentric radii**: dock **64** → droplet **52** (12 inset); sheet **48** → row droplet **36** (12 inset) → thumbnail **20** (16 inset); dock preview **24** → droplet **16** (8 inset); toast **48** → disc **32** (16 inset); switch **21** → knob **16** (5 inset). Gear **40**, poster **22**, strength track **5**.
- **Icons**: **24 px** inline/trailing (‹ › ↗ ✓ + and peek chevron), **32 px** row leads and toast moon, **36 px** gear. Every size renders a **2.5 px stroke**, with round caps and joins. ✓ uses level 1; ‹ › ↗ + use level 2.
- **App marks**: **96 px** on the dock, **48 px** in settings and previews.
- Dock padding **12**, cell gap **8**. Posters have **28 px gaps**, including beside the lifted poster.

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

`Wallpapers.java` builds the blur and its grid when a wallpaper is decoded.

### 6.2 Drawing a glass panel (`GlassView.onDraw`)

Draw these layers in order, each clipped to the panel's rounded rect by drawing *with* the shape. Use `drawRoundRect`, which is anti-aliased; don't use `clipPath`, which is aliased on HWUI.

1. **Shadow.** A static 9-patch behind the view, never animated:
   - Dock: offset y 18, blur 48, black 26 %, plus y 2 / blur 6 / 10 %.
   - Droplet y 10 / 26 / 20 %; gear y 10 / 28 / 22 %; sheet y 24 / 60 / 30 %; poster shadows are described in §8.
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

   `m` values: dock 1.05, droplet 1.10, gear 1.06, sheet 1.03, settings droplet 1.10, skeleton posters 1.04.
3. **Lens rim (dock and miniature dock previews).** Draw a 12 px-wide stroke (inset 6) with the same shader at **m = 1.16** and paint alpha 0.70. Content at the edge is displaced further than in the middle, which reads as refraction through a thick edge. Miniature dock previews use a 4 px band.
4. **Tint.** Fill with `glass-dark` at α (solved), then white at `frost`.
5. **Sheen.** A vertical `LinearGradient`: white 15 % at 0 → 4 % at 34 % → 0 at 60 % → 5 % at 100 %. The settings sheet uses a softer sheen, because rows of text sit under its top edge: 8 % → 2 % at 30 % → 0 at 60 % → 4 %.
6. **Milk (focus glass only).** Fill with white at `milk` (solved).
7. **Glint (dock droplet).** A `RadialGradient` ellipse, white 55 % → 0. Centre (8 % + 35 %, −30 % + 35 %) of the panel, 70 % × 70 % of its size, alpha 0.55.
   The wide settings droplet adds its own light after the milk: a 30 % radial peak centred at 34 % of the width, a top band fading out by 40 % of its height, and a 7 % thickness shade in the bottom 12 %. The light sits above the text and the shade below it, so the night pane has thickness without raising the milk target. It shares the dock droplet’s y 10 / blur 26 / 20 % shadow.
8. **Specular rim.** A 2 px stroke (inset 1) with a `LinearGradient` along the period's light angle. Stops: white `rimHi` at 0, 10 % at 26 %, 3 % at 55 %, `rimLo` at 100 %.

| Period | Light angle (CSS gradient angle) | rimHi | rimLo |
|---|---|---|---|
| Morning 06–11 | 135° (from top-left) | 0.78 | 0.20 |
| Day 11–17 | 170° (from top, slightly left) | 0.72 | 0.18 |
| Evening 17–21 | 225° (from top-right) | 0.66 | 0.16 |
| Night 21–06 | 180° (from top) | 0.44 | 0.10 |

**When glass moves**, its shader must follow it. A glass view's `translationX/Y` and `scaleX/Y` don't re-run `onDraw`, so during an animation that moves glass, the stage’s pre-draw listener calls `GlassView.syncAll()`, invalidating only panels whose mapped position or size changed. Recompute the matrix in `onDraw` from the view’s stage transform. Only the moving glass views redraw: the dock (448×232), the droplet and the settings droplet. When idle, nothing invalidates.

---

## 7. Adapting to bright and dark wallpapers

Glass alpha is **solved, not picked**. `Wallpapers.Tuning` uses the blur's **32 × 18** grid of mean colours, plus samples from the **sharp picture** for text that sits directly on it. Composite in **sRGB**, as the GPU does; use WCAG relative luminance for the ratio. The glass solver binary-searches for 14 iterations.

| Output | Rule | Target / bounds |
|---|---|---|
| Frost | `.10 − .06 · min(1, meanL / .25)`, rounded to .001 | .04–.10 |
| Dock tint | Greater of white 100 % on the dock's p90 cell, and white 62 % on its brightest cell | **4.5:1 / 3.2:1**, alpha .10–.78; re-solved for the actual dock width |
| Dock milk | Median cell mixed toward white until L ≥ .50, or .30 at night | Alpha .35–.92 |
| Gear tint / milk | White on p90 at 3:1; median cell → white until L ≥ .60, or .36 at night | Tint .10–.78; milk .35–.92 |
| Sheet tint | Brightest cell in **800,80,1000,920**, raw blur; effective frost `frost × .6 + .08` (including sheen) | White 78 % **4.5:1**, white 62 % **3:1**; alpha .16–.78 |
| Settings scrim | Brightest cell under **x 120–720, y 80–640**, mixed toward black | White **86 % at 5.2:1**, alpha .22–.86 |
| Row droplet milk | `milkEven`: sheet region's **darkest tenth** → white until L ≥ **.62**, or **.42** at night | Alpha floor **.74** by day / **.62** at night, ceiling .92; focused label **7:1**, value **4.5:1** |

“Night” here means mean picture luminance below **.07**, rather than the wall clock. The row's even milk solve prevents a bright patch of the blur from hiding dark text while preserving the dimmer night pane.

### Clock ink

Plain digits, the better ink, nothing else. No top scrim, shadow, halo, box or capsule is drawn behind the clock, on any wallpaper.

For each of the five tabular cells, sample p90 / p10 / p50 luminance pixels of the sharp image (digit band y **98–142**). Compare white against p90 and **#10151B** against p10, with the colon at 86 %. Whichever ink has the better **worst cell** wins. There is no contrast threshold or backing fallback. Worst and median contrast are diagnostic values, rather than a pass/fail target.

While the bedtime tint is on, the same comparison mixes both picture and ink toward **#1F0C01** at its current alpha. Re-evaluate those ten pixels on each tint step and crossfade the two clock views if the winning ink changes. Their rolling digits stay in step.

### Bottom text: ink first, gradient only when needed

The peek is one block; the row's title and meta are another. Sample **64 px windows, stepping 32 px**, across the sharp picture under each line box: peek **968–1000**, title **569–621**, meta **629–661**. Each window records the p90 and p10 pixels. Use only windows beneath the block's actual span, starting at x 120 and ending at its widest text (including the peek chevron). Row spans use the widest title and meta in the whole row, capped at the clipped lane's right edge, so the result stays stable while focus moves.

For each ink, composite at the run's opacity over its hardest window (white against p90, dark against p10). Score the weakest ratio / target: **peek 86 % at 4.5:1; title 100 % at 3:1; meta 86 % at 4.5:1**. If the better ink meets every target, use it with **no gradient**. Otherwise use white and solve the smallest black alpha meeting every target **+ .1:1**, rounded up to an 8-bit alpha step. There are no text shadows.

### Scrim shapes

| Scrim | Shape |
|---|---|
| Peek, only when needed | Solved alpha flat from y **960** to the bottom; smoothstep to 0 by y **800**, behind the dock |
| Row, only when needed | Solved alpha flat from y **560** to the bottom; smoothstep to 0 by y **360** |
| Both bottom gradients | Full strength to **120 px past the block's right edge**, then smoothstep to 0 over **640 px**; eight ramp intervals, vertical and horizontal shaders combined with `DST_IN` |
| Settings | Flat **#04080C** at the solved alpha over the full-screen blur |

Solutions are computed per decoded wallpaper, with bottom text re-solved when the content changes. Ambient fades the bottom gradients away. `app/tools/check-glass.sh` exercises the production solver over packed wallpapers, including sharp-picture text samples; its results are checks of that picture and content, not a fixed table of scrim values for an entire style.

---

## 8. Components

### App cell and droplet

- **Rest**:
  - White mono mark (92 % white, soft shadow): Stremio is a rounded diamond with the play triangle cut out; YouTube is a rounded rect with the triangle cut out.
  - Every other known app has a mark in `app/assets/marks/` (credits in `CREDITS.md` there), mapped by its Fire TV and Android TV package names in `MarkArt`. These are Simple Icons glyphs, or original glyphs for brands Simple Icons doesn't carry. Each is sized optically into the 96 px box: its bounds get a geometric mean of 64 units, no side over 84, plus a per-mark nudge. White layers in a mark are cut out at rest and white on focus.
  - Any other app gets a monogram: the first letter or digit of its name in Red Hat Display, cut from a 60-unit rounded tile. The focus colour is the dominant saturated hue of its icon. The icon is drawn at 32 px only to sample it, then freed.
  - `app/tools/preview-marks.sh` draws all of them on the Mac with the app's own data.
  - Label is white at **78 %**, 26/500.
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
- Light or dark ink is chosen per picture and re-evaluated under the bedtime tint (§7); no shadow or backing.
- Only the digits that change animate: the old digit moves up 18 px and fades (220 ms, in-curve), the new one rises from 18 px (340 ms, out-curve, delay 60).
- Draw it as a small custom view (about 300 × 72) so the minute tick invalidates nothing else.

### Peek line

The line under the dock names what is below:

| State | Peek line |
|---|---|
| Ready | `The Simpsons · S17 E8 ⌄` (title semibold, chevron at the end) |
| Loading | `Continue watching ⌄` |
| Signed out | 26/500 at level 2, without the chevron: “Sign in to Stremio from a computer to see what you were watching” |
| Offline with nothing cached, or nothing played in the last 7 days | Absent |

### Poster

- 176 × 264, radius 22, with a 1.5 px inner edge at white 16 %.
- A bottom gradient (black 62 % → 0 over 34 %) carries the progress bar.
- **Progress bar**:
  - Inset 16 px; 6 px tall, radius 3.
  - Track white 24 %; fill white, drawn to the cached progress fraction. The bar is hidden below 5 % progress.
  - Time left comes from `state.duration − state.timeOffset` in the libraryItem.
- **View tree**. Each level owns exactly one transform, so the animations never fight:
  - `poster`: x nudge, alpha
    - `shadow`: alpha, translationY, scale. A 9-patch soft shadow (y 26 / blur 52 / 55 % plus y 8 / blur 18 / 25 %), inset 6 px.
    - `lift`: translationY, scale (pivot top-left)
      - `tilt`: rotationY. Set `setCameraDistance(8000 · density)`.
        - `img`: clips, radius 22. Contains `art`, the poster bitmap (translationX, scale), and the static bottom gradient.
        - `edge`, `rimf`, `prog`
- **Focus is the poster itself lifting**, with no ring or outline:
  - It rises **25 px** and scales to **1.125** from its top-left corner.
  - Its shadow deepens: it follows the −25 px lift with an 18 px downward offset, scales to 1.125 and fades in.
  - A top-lit rim fades in: 2 px white at 55 %, masked from full at the top to 15 % at the bottom.
  - The artwork inside zooms to 1.05, so the frame and the picture separate slightly, which reads as depth.
- **Neighbours settle back**:
  - They stay **fully opaque**; nothing in the row is ever see-through.
  - They desaturate 15 % and dim 6 %. On Android, apply a `ColorMatrixColorFilter` to each poster's bitmap `Paint`: `ColorMatrix.setSaturation(0.85)` post-concatenated with a 0.94 scale on RGB.
  - A `ValueAnimator` animates saturation and dim with the focus change, using the lift’s duration and curve. It invalidates only posters whose filter changes.
  - All posters to the right move 22 px away, keeping the visible gaps at 28 px.
- **Arrival**:
  - The poster turns to face you: rotationY from −10°·dir to 0, 640 ms.
  - Its artwork drifts in 12 px from the direction of travel and settles over 760 ms. That is parallax, and it is transform only.
- There is no light sweep.
- **Title and meta**:
  - Each lives in a fixed-height clipped lane, so a long title never moves anything.
  - On a move, the current text drifts **the way you're travelling** (−48 px·dir) and fades in 150 ms behind the lane's soft edge.
  - The new text then arrives **from the other side** (+56 px·dir → 0, 540 ms out-curve): title after 120 ms, meta after 190 ms.
  - The outgoing text is gone before the incoming text is visible, so the two never overlap.
- **Held D-pad**:
  - Moves are throttled to one every 120 ms.
  - Each one interrupts and retargets from wherever things are. The strip takes 200 ms, the lift 220 ms, and the text uses 80 ms out / 200 ms in.
  - The tilt and parallax are skipped on held repeats, so a long scroll stays crisp and never queues.
- **Android**:
  - Each lane has incoming/outgoing TextViews for each ink, crossfaded and translated independently.
  - Each lane is a FrameLayout with `clipChildren` and 24 px horizontal fading edges (`setHorizontalFadingEdgeEnabled(true)`).

### Settings rows

The root has eyebrow **“Fresco”**, title **“Settings”**, and six destinations. It opens focused on Dock. The left column explains the focused item; the sheet has no internal section headings.

| Root row | Value / trailing slot | Context |
|---|---|---|
| Dock | “1 app” / “2 apps” etc. · › | “One to four apps sit in the glass dock on home. Home opens on the one you used last.” + miniature dock, no milk |
| Wallpapers | Current style · › | “The picture behind home follows the time of day, from morning to night. Choose photographs, paintings, abstract colour or space, or let it pick.” |
| Bedtime tint | Start – off time, or “Off” · › | Tint explanation + schedule graph |
| Remote buttons | What Netflix and Prime Video open, e.g. “Stremio, YouTube”; “Default” if neither is reassigned · › | Remote-buttons explanation |
| Stremio | Solid dot + “Connected”, or hollow dot + “Not signed in” | “Continue watching refreshes in the background, so home never waits for the internet.” Signed out: “Not signed in. Sign in from a computer: see the README. Only the login token is kept on the stick.” Select refreshes when connected |
| *(32 px gap)* Fire TV settings | “Amazon's own” · ↗ | “Amazon's own settings: display and sound, network, apps, remotes and devices. They open outside the launcher; Back brings you straight back here.” |

**Dock** — eyebrow “‹ Settings”. One row per app: 48 px mark, name, “n of N”. Left/Right moves the app, and focus follows it. Select once arms removal: **“Press again to remove”** replaces the stepper at 100 % ink, without chevrons. A second Select removes it; any other key disarms. The last app cannot be removed. After a 32 px gap, “Add an app” has a plus lead; at four it becomes the dimmed “The dock holds four apps”.

The paragraph is “The dock shows these apps from left to right. Left and Right move the focused app along the dock; Select takes it off.” The preview shows the focused app in milk. **Choose an app** (eyebrow “‹ Dock”) lists installed apps not already in the dock, with a trailing +. Its copy is “The app you choose goes at the right-hand end of the dock. The dock holds up to four.” The preview includes the candidate at the right; choosing it returns to that app's dock row.

**Wallpapers** — eyebrow “‹ Settings”. Nature, Art, Abstract, Space, Random, each with a **208 × 116**, radius-20 thumbnail of what it shows now and a 24 px ✓ on the current style. Rows are 148 px tall; Random reuses the four images as a 2 × 2 mosaic with 2 px gaps. Focus starts on the current style. Select applies at once, moving the check and starting the wallpaper transition; selecting the current style presses its check. The left item title is the style:

| Style | Explanation |
|---|---|
| Nature | “Photographs of lakes, forests, coasts and snow for each time of day, a different set each day of the week.” |
| Art | “Classic paintings chosen for the light at each time of day, a new one each day. Through December, winter and Christmas paintings take over.” |
| Abstract | “Abstract colour and form for each time of day, a different set each day of the week.” |
| Space | “Pictures of space for each time of day, a different set each day of the week.” |
| Random | “Each time of day picks one of the four styles. It keeps that picture until the next time of day, even after a restart.” |

**Bedtime tint** — eyebrow “‹ Settings”. “Every evening” has an amber switch; a 32 px gap precedes “Starts fading in”, “Full strength by”, “Turns off” and “Strength”; another 32 px gap precedes “Skip the tint in” (app count · ›). The four schedule rows fold away when disabled; the skip list stays.

On: “Dims and warms every app at night. The Amazon Music button turns it on or off; a second press goes back to the schedule.” Off: “Off in the evenings. The Amazon Music button still turns it on until [off time].” With the Music switch off, “Fast-forward on home” replaces “The Amazon Music button”.

Time rows have no edit mode: Up/Down moves focus, Left/Right changes by 15 minutes, increasing to 30 after more than six held repeats. Start/full stay within 17:00–02:00 and carry each other to preserve start ≤ full; off stays within 04:00–12:00. Strength changes by 10 % within 10–100 %; focusing it previews the real tint full-screen, leaving it restores the current state. Only the focused row shows its stepper chevrons. The graph reserves its marker band even when no time is focused. All six ticks remain; when disabled, it has no amber fill and its curve uses the 24 % track ink.

**Skip the tint in** — eyebrow “‹ Bedtime tint”. Each app has a mark, name, “Skipped” / “Tinted” and a check when skipped; Select toggles it. After a 32 px gap, “Add an app” opens **Choose an app** (eyebrow “‹ Skip the tint in”), listing installed apps not already on the list with trailing +. Choosing one returns with it focused. Both use: “Copy-protected apps black out their video under anything drawn over it, so the tint steps aside while one is open and comes back after.”

**Remote buttons** — eyebrow “‹ Settings”. “Netflix button” and “Prime Video button” show their target app and ›, with the button's own app mark as lead. After 32 px, **“Amazon Music button toggles the tint”** has a white switch. On: “The Netflix and Prime Video buttons can open any app instead. The Amazon Music button turns the bedtime tint on or off.” Off: “… instead. Amazon Music opens as usual; fast-forward on home turns the tint on or off.”

Each button opens its own picker (eyebrow “‹ Remote buttons”), with “Choose what the [name] button opens. By default it opens [name].” The first row is its own app, value **“Default”**; after 32 px come all other launchable apps. The current choice has ✓, and the picker **opens focused on that choice**, scrolling it into view. Selecting a choice returns to Remote buttons.

**Pages.** Back pops one page and restores focus to the row that opened it; Back at root closes to the gear. The eyebrow names that Back destination, with a hanging ‹. Push: current page −72 px / alpha 0 in 170 ms; incoming page +104 px → 0 / alpha 1 in 440 ms, with the first nine rows staggered 24 ms. Pop mirrors it. Keys are ignored during the 170 ms swap, and the left column crossfades.

**Scrolling.** The sheet hugs content, up to the frame, and clips through a custom viewport. Long lists fit whole rows plus 12 px padding at either end. Scroll by whole-row offsets, keeping the next row available; there is no 150 px focus band. Scrolls take 420 ms, or 180 ms held; held focus moves take 150 ms without droplet stretch. A 56 px top fade / 72 px bottom fade appears only at a clipped edge. Targets use the logical layout during a schedule fold.

**Collapsing the schedule.** Focus stays on “Every evening”. The four rows fold bottom-up (150 ms, 12 ms stagger, alpha 0, y −14, scaleY .94); from 110 ms the skip row glides up **384 px** (four rows × 88 + the 32 px group gap) in 460 ms. Expansion opens that space in 480 ms from 40 ms, then unfolds rows from 170 ms, 50 ms apart, over 340 ms. Reversals retarget from the current state, guarded by a generation counter. Layout commits once at the end of a collapse (`GONE` and translations reset), or at the start of expansion. The smaller/larger fixed glass panes crossfade; they are cached by height rather than resized every frame.

**Switches** are 76 × 42 with a radius-16 knob inset 5. The knob translates **34 px** over 220 ms; on-layers crossfade by alpha. Focused labels use dark 100 % and values dark 70 %; dimmed rows use 62 % white at rest. Row ink changes when focus arrives, rather than animating colour.

### Tint overlay

- A full-screen `TYPE_ACCESSIBILITY_OVERLAY` window: a plain source-over colour layer. The shipped path draws an opaque RGB_565 surface once and animates the window alpha through SurfaceFlinger. This is all the stick can do. The SurfaceFlinger colour matrix is blocked and Fire OS night display is disabled, both confirmed on the device, so there is no multiply and no true colour shift.
- **Default recipe, "Dim + amber"**: `#1F0C01` at alpha = **0.58 × strength**. Mostly dimming, with just a touch of amber. Blue light falls mainly because *all* light falls, which keeps haze and black-lift to a minimum. At 100 %:

  | Input | Output | Notes |
  |---|---|---|
  | Black | about rgb(18, 7, 1) | Within the rgb(18, 8, 2) budget |
  | White | about rgb(125, 114, 108) | Gently warm, not brown |
  | Contrast loss | – | Far smaller than with the amber recipe |

- The strength setting scales the shipped recipe's alpha. There is no recipe picker.
- On schedule, alpha ramps linearly from start to full; at the off time it fades out over 2 s.
- Music button (when enabled), or Fast-forward on home: on → the configured strength over 900 ms; off → 0 over 600 ms. A second press drops the override when it matches the schedule. These are one-shot full-screen alpha animations, cheap on this GPU.

### Toast (tint confirmation)

- **On**:
  - Amber moon (filled) in an amber 14 % disc.
  - "Bedtime tint on" / “Until [off time]”, or “Back to your schedule, until [off time]”.
- **Off**:
  - Hollow moon in a white 8 % disc.
  - "Bedtime tint off" / “Off for tonight, back [start time]”, or “Back to your schedule, from [start time]”. When the schedule is disabled: “Until you turn it on again”.
- Smoked pill `#18110C` at 86 %, with an inner top highlight 1.5 px `#FFECD6` at 16 % and an outline 1.5 px at 8 %.
- Drawn above the tint in its own translucent accessibility-overlay window, so the toast stays clear while the picture is dimmed.
- Visible for about 2.4 s in total (§10).

---

## 9. Focus model and D-pad map

Focus is always on exactly one thing, and it is always shown by milk glass, a lifted poster or the settings droplet. A Home press lands on the **last-launched app**, falling back to the first dock app if the saved app is absent, with the row closed.

| Zone | Left | Right | Up | Down | Select | Back |
|---|---|---|---|---|---|---|
| Dock (1–4 apps) | prev app / bump | next app / bump | Gear | Open row (if ready or loading) / bump | Launch app | nothing |
| Gear | bump | bump | bump | Dock | Launcher settings | nothing |
| Row | prev poster / bump | next poster / bump | Dock (rise) | bump | Open `stremio:///detail/…` | Dock (rise) |
| Ambient | any key restores home and is consumed | | | | | |
| Settings | adjust − | adjust + | prev row / bump (scrolls by whole rows) | next row / bump (same) | toggle / act / push a sub-page / open Fire TV settings | pop a page (focus returns to the row that opened it), else close to Gear |

**Bump** is the edge feedback: the focused thing moves 10 px toward the pressed direction and back, 240 ms. It is an additive transform, so it combines with focus scale.

Rewind on home (any zone) enters ambient. The Music button toggles the tint from anywhere when its switch is on; Fast-forward does the same on home. Home from anywhere runs the reveal (§10).

---

## 10. Motion

Most motion is `translation`, `scale`, `rotation` or `alpha`. Moving glass updates its shader matrix (§6.2); poster focus also animates a small colour filter, and the tint uses window alpha. Every animation starts from the current visual state, so a new key press interrupts cleanly.

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
| | | Label white / dark: alpha | 0.78 ↔ 0 / 0 ↔ 1 | 200 / 220 | – | std |
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
| **Poster focus** (tap) | Left/Right in the row | Strip: translationX | → −i·204 | 540 | – | std |
| | | Focused `lift`: translationY, scale | → −25, → 1.125 | 480 | – | out |
| | | Focused `shadow`: alpha, translationY, scale | → 1, → −7, → 1.125 | 480 | – | out |
| | | Focused `rimf`: alpha | → 1 | 320 | – | out |
| | | Focused `tilt`: rotationY | −10°·dir → 0 | 640 | – | out |
| | | Focused `art`: translationX (scale 1.05) | 12·dir → 0 | 760 | – | out |
| | | Unfocused `lift`, `shadow`, `rimf` | → 0 / 1, → 0, → 0.94 | 340 | – | std |
| | | Others: saturation, brightness (ColorMatrix, ValueAnimator); posters right of focus: translationX | 1 → 0.85, 1 → 0.94 (focused: back to 1, 480 out); +22 | 340 | – | std |
| | | Passed posters (leaving behind the left edge): alpha | → 0 | 260 | – | in |
| | | Title, meta out: translationX, alpha | → −48·dir, → 0 | 150 | – | in |
| | | Title in: translationX, alpha | 56·dir → 0, 0 → 1 | 540 | 120 | out |
| | | Meta in: translationX, alpha | 56·dir → 0, 0 → 1 | 540 | 190 | out |
| **Poster focus** (held) | Key repeat, ≥ 120 ms apart | Strip | → target | 200 | – | (0.25, 0.6, 0.3, 1) |
| | | `lift` / `shadow` up; down | as above | 220; 160 | – | out; std |
| | | Text out / in | ∓16 / ±18 px | 80 / 200 | 0 / 60 | in / out |
| | | Tilt, parallax | skipped | – | – | – |
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
| | | Sheet content: translationY | whole-row offsets, leaving room for the next row; clamped to content | 420 (held: 180) | – | std (held: (0.25, 0.6, 0.3, 1)) |
| | | Edge fades (top 56 / bottom 72 px) | on only while content is clipped on that side | 260 | – | std |
| **Settings droplet** (held) | Key repeat, ≥ 110 ms apart | Droplet: translationY | → y (no stretch) | 150 | – | (0.25, 0.6, 0.3, 1) |
| **Schedule off** | Select on "Every evening" | Knob: translationX; on-layers: alpha | 34 → 0; 1 → 0 | 220 | – | std |
| | | 4 schedule rows (bottom-up): alpha, translationY, scaleY | → 0, → −14, → 0.94 | 150 | 12·j | (0.2, 0.6, 0.3, 1) |
| | | Everything below: translationY | 0 → −384, then commit (GONE + reset) | 460 | 110 | (0.45, 0, 0.2, 1) |
| **Schedule on** | Select on "Every evening" | Everything below: translationY | −384 → 0 (rows VISIBLE first) | 480 | 40 | std |
| | | 4 schedule rows (top-down): alpha, translationY, scaleY | 0 → 1, −18 → 0, 0.92 → 1 | 340 | 170 + 50·j | out |
| | | Context column (section change): out | alpha → 0 | 120 | – | in |
| | | Context column (section change): in | alpha 0 → 1, y +10 → 0 | 280 | 80 | out |
| **Value change** | Left/Right | Old value: translationX, alpha | 0 → −18·dir, → 0 | 120 | – | in |
| | | New value: translationX, alpha | 18·dir → 0, 0 → 1 | 200 | – | out |
| | | Strength fill: scaleX | → s/100 | 220 | – | std |
| **Strength preview** | Focus on/off the strength row | Tint overlay: alpha | → 0.58·s/100 / → its real state | 320 | – | std |
| **Style chosen** | Select on a Wallpapers choice | Old ✓: alpha | → 0 | 120 | – | in |
| | | New ✓: alpha, scale | 0 → 1, 0.6 → 1 | 280 | – | out |
| **Page push / pop** | Select a destination / Back | Page out: translationX, alpha | → −72·dir, → 0 | 170 | – | in |
| | | Page in: translationX, alpha | 104·dir → 0, 0 → 1 | 440 | – | out |
| | | Rows in (first 9): extra translationX | 28·dir → 0 | 420 | 40 + 24·i | out |
| **Tint toast** | Music button (enabled) / Fast-forward on home | Tint overlay: alpha | → 0.58·s/100 (on) / → 0 (off) | 900 / 600 | 80 / 0 | std |
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

Skeleton breathing is finite (five repeats of a 1400 ms cycle, plus the short stagger) even if the network never answers. If the wallpaper change happens while home isn't visible, just swap the bitmaps.

**Reduced motion.** There is no reduced-motion toggle in the shipped app.

### How the reveal works on the stick

1. The accessibility service puts up its black cover and clicks the tile; that's existing behaviour.
2. In `onStart`, the launcher sets its own full-screen black **veil** to alpha 1. Once resumed and its bitmaps are ready, it resets home state and starts the hand-over on a drawn frame.
3. On the first drawn frame (`ViewTreeObserver.OnDrawListener`, same process as the service), it tells the service to remove the cover, then starts the reveal.
4. The black hand-over is invisible, and the reveal never waits on another process.

---

## 11. States

| State | What you see | Notes |
|---|---|---|
| Home | Photo, clock, gear, dock with the droplet, peek | Idle: no redraw except the minute tick |
| Continue watching | Dipped plane, title and meta, posters | Row data comes from the on-stick cache; home never waits on the network |
| Row loading (first sync, nothing cached) | Peek says "Continue watching"; the row shows 6 breathing glass skeletons and two glass bars | Down allowed, Select inert |
| Signed out | Quiet peek line pointing to a computer; row absent; Down bumps | Stremio row says “Not signed in”; its context points to the README |
| Offline with nothing cached | No peek, no row, Down bumps | As if the feature didn't exist |
| Nothing played in the last 7 days | Same as offline: no peek, no row | The row lists only titles played in the last 7 days, most recent first (`state.lastWatched`, per show) |
| Ambient | Photo and clock only, settled into the top-right corner | Any key restores and is consumed. Bottom gradients fade away; the clock has no backing |
| Tint active | The whole screen is dim amber, launcher included | Scheduled ramp, Music button (enabled), or Fast-forward on home |
| Tint toast | Smoked pill top-centre, about 2.4 s | Over any app (see §12, item 3, for copy-protected apps) |
| Launcher settings | Blurred wallpaper, eyebrow, page title and context left, glass sheet right. Opened by Select on the gear | Back closes to the gear |
| Fire TV settings (Amazon's) | Amazon's app in front | Back returns to launcher settings, Fire TV row focused |
| Skip the tint in (sub-page) | Apps with “Skipped” / “Tinted” and checks, then “Add an app”; Back destination in the left eyebrow | Back returns to Bedtime tint with “Skip the tint in” focused |
| Picker | Page title “Choose an app”, eyebrow “‹ Skip the tint in” | Back returns to "Add an app"; picking an app returns with that app focused |
| Schedule off | "Every evening" off; the 4 schedule rows hidden; graph in 24 % track ink, without amber | Music (when enabled) and Fast-forward on home still work; the skip list stays |
| Wallpaper change | Defocus, swap, refocus over about 2.3 s | At most 4 times a day, plus whenever a style is chosen. Choices made during a change are followed once it ends; settings closed mid-change stay on the blur until the new photo is up |

---

## 12. Conflicts and how they were resolved

1. **Glass on a moving panel.** The dock dips 400 px; a static shader would show the wrong patch of sky. Moving glass views redraw their shader each animation frame. Only these few small views do it, and only while they move, so idle stays redraw-free.
2. **Ambient keeps "only wallpaper and clock".** Nothing else is added. The clock glides 112 px into the corner the gear vacated, so the screen reads as finished rather than as missing pieces.
3. **The toast over copy-protected apps.** The toast is an overlay too, so it could trigger the same blackout the tint list exists to avoid. **When a skipped app is in front, the Music button toggles silently: no toast, no tint.** The toast can't be wallpaper glass over another app either; Android 9's accessibility service can't read other apps' pixels. So it is a self-contained smoked pill.
4. **"Blacks don't glow" versus visible warmth.** A source-over overlay can only blend toward its own colour. Anything warm enough to read as amber therefore lifts black and veils dark scenes; the earlier `#4A1E00` recipe lifted black to about rgb(41, 16, 0). A real colour shift would avoid this, but it was tested and is impossible on the stick (the SurfaceFlinger matrix is blocked and night display is disabled). So the default is now **"Dim + amber"**: `#1F0C01` at up to 0.58. It cuts blue mostly by cutting all light, and keeps black at about rgb(18, 7, 1) with whites gently warm. The strength row previews the shipped tint full-screen so it can be tuned by eye on the TV.
5. **One decoded wallpaper versus a crossfade.** A sharp-to-sharp crossfade would need 16 MB. The change goes through the small blurred copies instead: peak memory is one full bitmap plus two 518 KB copies.
6. **Signed-out row.** The spec lists signed out next to "row simply absent". The row *is* absent. One quiet line under the dock explains why, because otherwise there is no clue it exists. Offline-with-nothing-cached shows nothing at all.
7. **Back on home.** The spec says Back does nothing on home. In the row, Back rises to the dock, which still keeps you on home and never falls through to Amazon home.
8. **Two settings behind one gear.** The spec originally split them with tap (Fire TV) and hold (launcher). Holds are undiscoverable on a remote and needed a fill ring to explain themselves. Now Select always opens launcher settings, and Amazon's settings are one clearly marked row inside (↗, separated by a 32 px gap). That costs one extra press to reach Amazon's settings, and in exchange there is no hidden gesture.
9. **Text straight on photos.** The blur grid cannot see fine painted detail. The clock and bottom blocks therefore sample the sharp picture and choose between two inks. Only bottom text may add a solved gradient when neither holds; none of that text uses a shadow (§7).
10. **Alpha on groups.** A full-plane offscreen layer would cost 8 MB. Home groups avoid that layer and fade their parts; posters also fade children individually, because Fire OS loses their rounded clip when the group is faded. Small overlapping groups can use transient layers, released after the fade.

---

## 13. Performance and memory

| Element | Memory | Draw cost | Idle |
|---|---|---|---|
| Wallpaper (1920×1080 ARGB_8888) | 8.3 MB | One bitmap quad | Static |
| Blurred copy 480×270 (+ second during a change) | 0.5 MB (+0.5) | Sampled by glass views and the settings layer | Static |
| Luminance grid 32×18 | 2 KB | – | – |
| Dock + droplet + gear (GlassView ×3) | Negligible | A few `drawRoundRect`s with shaders | Static |
| Shadows (9-patch PNGs) | ~40 KB total | One draw each | Static |
| Wallpapers sub-page thumbnails | 4 × 240×135 (decoded at `inSampleSize = 8`): ~518 KB, only while that page is up; released when it pops, settings close or the launcher leaves | Bitmap quads | Static |
| Posters (~10) | Decode `inSampleSize = 2` (250×375), scale to 208×312 (1.125 lift × 1.05 artwork zoom × 176×264, sharp when focused), keep only the scaled copy: ~260 KB each, **~2.6 MB** | Bitmap quads | Static |
| Fonts | 2 × 98 KB | – | – |
| Clock view | – | ~300×72 invalidate per minute | The only idle redraw |
| Tint overlay | – | One full-screen colour quad | Static |
| Transient hardware layers | ≤ 0.4 MB, only during group fades | – | Released |
| **Total images on screen** | **≈ 11–12 MB** | | |

When another app is in front, release the wallpaper, the blurred copy and the posters. That leaves the ~15 MB target for the accessibility service plus the tint overlay.

---
