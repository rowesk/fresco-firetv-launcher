# Installing Fresco: instructions for agents

You're helping someone put Fresco on their Fire TV. You'll work from their computer over Wi-Fi ADB; they hold the remote. This file says what to do, what to ask them, and what never to do. `install.sh` does the mechanical part; the judgement is yours.

## Ground rules

- **Ask before you press anything on the TV.** Read-only commands (`getprop`, `dumpsys`, `screencap`) are fine at any time. Installs, `input keyevent`, settings changes and reboots interrupt whoever is watching, so check the TV is free first.
- **Never disable, hide or uninstall Amazon's launcher** (`com.amazon.tv.launcher`) or any other system package. Fresco works *in front of* Amazon home, and Amazon home is the safety net if Fresco is ever removed. Disabling it can leave the TV with no home screen.
- **Never overwrite `enabled_accessibility_services`.** Append to it. The person may rely on other services.
- **Passwords never go in chat.** For Stremio, have the person run `app/tools/stremio-login.sh` in their own terminal (it prompts without echoing), or use your harness's secret-input mechanism if it has one. Only a login token is stored on the TV.
- **"Can't connect" usually isn't "offline".** Fire TVs drop Wi-Fi ADB while their Wi-Fi naps. Retry for a minute before telling the person anything is wrong, and ask them to press a button on the remote to wake it.
- **Everything is reversible.** Keep a short note of each change you make so you can tell the person exactly how to undo it (`./install.sh --uninstall <ip>` undoes the install).

## 1. Check the device

Ask which Fire TV they have, or read it once connected (`getprop ro.product.model`). Fresco needs **Fire OS** (Android-based), version 6 or later:

| Works | Doesn't work |
|---|---|
| Fire TV Stick 4K Max (1st gen, AFTKA, Fire OS 7). **Tested.** | Fire TV Stick 4K Select and other **Vega OS** devices: they can't run Android apps at all |
| Fire TV Stick 4K, Lite, 3rd gen, Cube, Fire TV Omni/4-Series (Fire OS 6–8). Expected to work, not yet tested | Fire tablets (it's a TV launcher) |

If the device reports an Android API below 25, stop: it's too old.

## 2. What the person does on the TV

Give them these steps in plain words, one at a time:

1. **Turn on Developer options.** *Settings → My Fire TV → About*, then press Select on the device name seven times until it says "You are now a developer".
2. **Turn on ADB debugging.** *Settings → My Fire TV → Developer options → ADB debugging: ON.* ("Apps from unknown sources" isn't needed for ADB installs.)
3. **Find the IP address.** *Settings → My Fire TV → About → Network.* Ask them to read it to you.

Their computer needs `adb` (macOS: `brew install android-platform-tools`; Linux: the `android-tools`/`adb` package; Windows: Google's Platform Tools zip) and must be on the same network.

## 3. Install

From the root of this repository:

```sh
./install.sh <ip>
```

It connects (retrying while the TV wakes), checks the device, downloads the latest release and verifies its checksum, installs it, turns on Fresco's accessibility service without touching any others, and opens Fresco.

The first connection shows an **"Allow USB debugging?"** prompt on the TV. Tell the person to tick *Always allow from this computer* and choose *Allow*. The script waits for it.

If you'd rather do it by hand, the steps are exactly:

```sh
adb connect <ip>:5555
adb -s <ip>:5555 install -r Fresco.apk
# append, don't replace:
current=$(adb -s <ip>:5555 shell settings get secure enabled_accessibility_services)
adb -s <ip>:5555 shell settings put secure enabled_accessibility_services "$current:io.github.rowesk.fresco/io.github.rowesk.fresco.FrescoService"
adb -s <ip>:5555 shell settings put secure accessibility_enabled 1
```

(If `current` is `null` or empty, put only Fresco's entry.)

## 4. Pin the tile (the person, with the remote)

Fire OS doesn't let apps replace the Home button. Fresco's accessibility service sees Amazon home appear and taps Fresco's tile on it, behind a black cover, so it feels instant. That needs **Fresco's tile on Amazon's home row**:

> Press the **Apps** button (three squares). Highlight **Fresco**, press the **Menu** button (three lines), and choose **Move to front**.

Without the tile, Home still reaches Fresco, but Android makes it wait up to 5 seconds.

## 5. Set it up

Fresco's settings are on the TV: the cog at the top right of its home screen. Walk the person through:

- **Dock**: the 1–4 apps they actually use.
- **Wallpapers**: Art, Nature, Abstract, Space or Random.
- **Remote buttons**: what the Netflix and Prime Video buttons open (default: their own apps), and whether the Amazon Music button toggles the bedtime tint (default: yes).
- **Bedtime tint**: on or off, when it starts, when it's at full strength, when it turns off, and how strong. Copy-protected apps (Prime Video, iPlayer and others) are skipped automatically, because their video is protected and mustn't be drawn over. The list is editable under *Skip the tint in*.

**Stremio users** can show their Continue watching row on the home screen:

```sh
DEVICE=<ip>:5555 app/tools/stremio-login.sh      # prompts for email and password
```

The script needs the source repository (not just the APK) and `python3` and `curl`.

## 6. Check it worked

- `adb -s <ip>:5555 shell dumpsys activity activities | grep mResumedActivity` should show `io.github.rowesk.fresco/.HomeActivity` after Home is pressed.
- A screenshot: `adb -s <ip>:5555 shell screencap -p /sdcard/s.png && adb -s <ip>:5555 pull /sdcard/s.png && adb -s <ip>:5555 shell rm /sdcard/s.png`.
- Ask the person to press Home from inside another app, and again after turning the TV off and on. Both should land on Fresco.

## Known quirks

- **After every update, Fresco's tile disappears from Amazon's Apps grid** and Fire OS keeps showing the old icon until the TV restarts. Fresco itself works fine. A restart (hold Select + Play for 5 seconds) brings the tile back; re-pin it if it fell off the home row.
- **Fire OS squeezes Fresco's icon into a square** on a grey card. Sideloaded apps can't have Amazon's full-width tiles.
- **`com.amazon.venezia`** (Appstore and app library) is protected: `am force-stop` and `am kill` both fail. Don't try to work around it.
- **Android sometimes doesn't rebind an updated accessibility service.** `install.sh` switches Fresco's service off and on again after each install for this reason.
- **The tint can't colour-shift the picture.** Fire OS has no night light and blocks colour matrices, so the tint is an amber overlay. It's dark amber so blacks don't glow.

## Undo

```sh
./install.sh --uninstall <ip>
```

This removes Fresco from the accessibility services (keeping any others), uninstalls it, and returns to Amazon's home screen. Nothing else was changed.

## Building from source (optional)

`app/build.sh` builds the APK without Gradle (JDK 17, Android SDK platform 28 and build-tools 34.0.0, Python 3 with Pillow and numpy, and `cwebp`). The first build downloads the Nature and Abstract photos from Pexels and Unsplash, because their licences don't allow committing them. An APK you build is signed with your own key, so to switch between it and a release you'll need to uninstall first.
