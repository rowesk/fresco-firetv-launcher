<h1 align="center">Fresco</h1>

<p align="center"><b>A Fire TV launcher that turns your TV into a painting.</b><br>
A classic painting for the time of day, your apps and a clock. No ads, autoplaying trailers or sponsored rows.</p>

<p align="center"><img src="docs/screenshots/home.jpg" alt="Fresco's home screen: a classic painting fills the TV, with a small glass dock of apps at the bottom left and the time at the top right" width="100%"></p>

---

## What it does

### Replaces Amazon's home screen

Home always opens Fresco, from inside any app and straight after the TV wakes. Amazon's launcher stays installed underneath, untouched, so there are no ads, autoplaying trailers or sponsored rows to scroll past.

### Paintings that follow the time of day

20 classic paintings rotate through morning, day, evening and night: Turner's *Fighting Temeraire* at sunset, Rembrandt's *Night Watch* by candlelight, Constable's *Hay Wain* on a summer afternoon. Each is cropped by hand so no face or figure is cut off, and a set of 8 winter and Christmas paintings takes over in December. Nature photographs, Abstract textures, Space (NASA and Webb) and Random are in settings too.

<p align="center"><img src="docs/screenshots/paintings.jpg" alt="Eight of the paintings: Turner, Rembrandt, Constable, Aivazovsky, Claude Lorrain, Grimshaw, Bruegel and Cuyp" width="100%"></p>
<p align="center"><img src="docs/screenshots/wallpaper-styles.jpg" alt="Choosing a wallpaper style: Nature, Art, Abstract, Space or Random" width="70%"></p>

### A bedtime tint over every app

Fire OS has no night light. Fresco dims and warms the whole picture, in every app and video, fading in on your schedule and out in the morning. The Amazon Music button (or fast-forward on the home screen) switches it on or off, and a second press goes back to the schedule. Copy-protected apps such as Prime Video are skipped automatically.

<p align="center"><img src="docs/screenshots/settings-tint.jpg" alt="Settings with the bedtime tint schedule drawn as a chart" width="49%"> <img src="docs/screenshots/bedtime-tint.jpg" alt="The bedtime tint itself, dim and warm over the screen" width="49%"></p>

### A dock of your apps

One to four apps of your choice, each drawn as a white mark that turns its brand colour when selected. Netflix, Prime Video, BBC iPlayer, ITVX, Disney+, Spotify, Plex and more than 30 others have their own marks; anything else gets a monogram. Every other app is still one press away on the remote's Apps button.

<p align="center"><img src="docs/screenshots/dock.jpg" alt="The Dock settings page: Stremio, YouTube and BBC iPlayer, with Add an app" width="70%"></p>

### Remote buttons that open your apps

Point the Netflix and Prime Video buttons at any installed app, or leave them as they are.

<p align="center"><img src="docs/screenshots/remote-buttons.jpg" alt="Remote buttons in settings: Netflix button, Prime Video button and Amazon Music button" width="70%"></p>

### Continue watching, for Stremio

Press Down on the home screen for the shows you've played in the last week; one press resumes the episode.

<p align="center"><img src="docs/screenshots/continue-watching.jpg" alt="The Continue watching row: posters beneath the painting" width="70%"></p>

### Ambient mode

Press Rewind and everything disappears except the painting and the clock. Any button brings the home screen back.

<p align="center"><img src="docs/screenshots/ambient.jpg" alt="Ambient mode: only the painting and the clock" width="70%"></p>

### Small and reversible

Liquid-glass design made for viewing from the sofa, with Home in under half a second. It's plain Java with no libraries, and it releases its pictures while you're watching something else. No account, no tracking and no analytics. Nothing is rooted or disabled, and one command puts the Fire TV back exactly as it was.

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
