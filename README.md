<h1 align="center">Fresco</h1>

<p align="center"><b>A Fire TV launcher that turns your TV into a painting.</b><br>
No ads, no autoplaying trailers, no sponsored rows. Just a masterpiece for the time of day, your apps, and a clock.</p>

<p align="center"><img src="docs/screenshots/home.jpg" alt="Fresco's home screen: a classic painting fills the TV, with a small glass dock of apps at the bottom left and the time at the top right" width="100%"></p>

---

## Why you'll like it

**You'll never see Amazon's home screen again.** Press Home from anywhere, including straight after the TV wakes up, and you land on Fresco. Amazon's launcher stays installed underneath, untouched; Fresco simply steps in front of it every time.

**A different painting every morning, afternoon, evening and night.** Turner's *Fighting Temeraire* at sunset, Rembrandt's *Night Watch* by candlelight, Constable's *Hay Wain* on a summer afternoon. 20 masterpieces rotate through the day, each hand-cropped so no face or figure is cut off, and in December a set of 8 winter and Christmas paintings takes over. Prefer photographs? Choose Nature, Abstract or Space (NASA and Webb) instead, or Random.

**A bedtime tint for the whole TV.** Fire OS has no night light, so Fresco brings one: a warm, dim amber layer over every app and video that fades in on your schedule and fades out in the morning. The Amazon Music button switches it on or off. Copy-protected apps like Prime Video are skipped automatically.

**Only the apps you use.** A small glass dock holds one to four apps of your choosing, each drawn as a clean white mark that wakes in its brand colour when you land on it. Everything else is still one press away on the remote's Apps button.

**Your remote, your way.** Make the Netflix and Prime Video buttons open the apps you actually use, or leave them alone.

**Pick up where you left off.** If you use Stremio, press Down for your Continue watching row, and one press resumes the episode.

**A screen you'll want to leave on.** Press Rewind and everything disappears except the painting and the clock.

**Calm, quick and light.** Liquid-glass design built for viewing from the sofa, and Home in under half a second. It's plain Java with no libraries, and it lets go of its pictures while you're watching something else. No account, no tracking, no analytics.

**Nothing permanent.** No root, nothing disabled. One command puts your Fire TV back exactly as it was.

## A look around

| | |
|---|---|
| ![Continue watching row with posters under the painting](docs/screenshots/continue-watching.jpg) | ![Ambient mode: only the painting and the clock](docs/screenshots/ambient.jpg) |
| **Continue watching**: press Down on the home screen | **Ambient mode**: press Rewind |
| ![Settings with the bedtime tint schedule drawn as a chart](docs/screenshots/settings-tint.jpg) | ![The bedtime tint previewed over settings](docs/screenshots/bedtime-tint.jpg) |
| **Bedtime tint**: start, full strength, off and strength, drawn as you change them | **The tint itself**: dim, warm, and over every app |
| ![Choosing a wallpaper style](docs/screenshots/wallpaper-styles.jpg) | ![The dock settings page](docs/screenshots/dock.jpg) |
| **Wallpapers**: Art, Nature, Abstract, Space or Random | **Dock**: choose up to four apps |

<p align="center"><img src="docs/screenshots/paintings.jpg" alt="Eight of the paintings: Turner, Rembrandt, Constable, Aivazovsky, Claude Lorrain, Grimshaw, Bruegel and Cuyp" width="100%"></p>

## Install

### The easy way: ask your AI agent

If you use a coding agent (Claude Code, Codex, Cursor and so on) on a computer on the same Wi-Fi as your Fire TV, say:

> Install Fresco on my Fire TV. Follow AGENTS.md in https://github.com/rowesk/fresco-firetv-launcher

[AGENTS.md](AGENTS.md) tells it exactly what to do, what to ask you, and what never to touch.

### By hand

1. **On the Fire TV:**
   - Open *Settings → My Fire TV → About* and press Select on the device name seven times. Developer options appear.
   - Turn on *Developer options → ADB debugging*.
   - Note the IP address under *About → Network*.
2. **On a computer on the same Wi-Fi**, with [`adb`](https://developer.android.com/tools/releases/platform-tools) installed (macOS: `brew install android-platform-tools`):

   ```sh
   git clone https://github.com/rowesk/fresco-firetv-launcher && cd fresco-firetv-launcher
   ./install.sh 192.168.1.50        # your Fire TV's IP address
   ```

   Choose **Allow** when the TV asks about USB debugging. The script downloads the latest release, checks it, installs it and switches it on.
3. **Pin the tile.** Press the remote's Apps button, highlight Fresco, press Menu (☰) and choose **Move to front**. This makes Home instant.
4. **Make it yours.** Open settings (the cog, top right) to choose your dock apps, wallpapers, buttons and bedtime tint.

Stremio users can add their Continue watching row with `DEVICE=192.168.1.50:5555 app/tools/stremio-login.sh`. It asks for your password on your computer and stores only a login token on the TV.

### Which Fire TVs?

| | |
|---|---|
| ✅ **Tested** | Fire TV Stick 4K Max (1st gen), Fire OS 7 |
| 🟡 **Should work** | Other Fire OS 6–8 devices: Fire TV Stick 4K, Lite, 3rd gen, Cube, Fire TV Omni and 4-Series TVs. Tell us how it goes |
| ❌ **Can't** | Vega OS devices such as the Fire TV Stick 4K Select, which can't run Android apps |

### Uninstall

```sh
./install.sh --uninstall 192.168.1.50
```

Fresco is removed and Home goes back to Amazon's home screen. Nothing else was changed.

## How it works

Fire OS doesn't let apps replace the Home button, so Fresco uses an accessibility service:

- **Home.** When Amazon's home screen appears, the service taps Fresco's tile on it behind a black cover, so it feels instant. It also checks when the screen wakes up, because Amazon home is often already showing then and nothing else would trigger.
- **The tint** is an accessibility overlay, which sits above every app. Fire OS has no night light and blocks colour matrices, so this is the only way to warm the picture.
- **The glass** is drawn from a pre-blurred copy of each wallpaper, magnified and lensed, because Fire OS can't blur live. Tint, frost and contrast are solved for every picture, so text stays readable on bright and dark paintings alike.
- **The app** is plain Java with no libraries and no Gradle. It's built with aapt2, javac and d8 in [`app/build.sh`](app/build.sh).

The design system is in [docs/DESIGN.md](docs/DESIGN.md).

## Building it yourself

```sh
brew install openjdk@17 webp && pip3 install pillow numpy   # plus Android SDK platform 28 and build-tools 34.0.0
app/build.sh                                                # makes app/Fresco.apk
./install.sh 192.168.1.50 app/Fresco.apk
```

The first build downloads the Nature and Abstract photos from Pexels and Unsplash. Their licences allow them in an app but not redistributed as files, so they aren't in this repository.

## Credits

- **Paintings**: public domain, via Wikimedia Commons ([credits](wallpapers/art/CREDITS.md)).
- **Space**: NASA (public domain), and ESA/Webb, ESA/Hubble and ESO (CC BY 4.0) ([credits](wallpapers/space/CREDITS.md)).
- **Nature and Abstract**: photographers on Pexels and Unsplash ([nature](wallpapers/nature/CREDITS.md), [abstract](wallpapers/abstract/CREDITS.md)).
- **App marks**: most are from [Simple Icons](https://simpleicons.org) (CC0); the rest are original designs ([credits](app/assets/marks/CREDITS.md)).
- **Type**: Red Hat Display and Red Hat Text, SIL Open Font License.

Fresco isn't affiliated with Amazon, Stremio, YouTube or Netflix. Their names and marks belong to them.

## Licence

The code is [MIT](LICENSE). The wallpapers and fonts keep their own licences, listed above.
