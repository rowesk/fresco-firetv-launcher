#!/bin/zsh
# Builds and signs Fresco without Gradle: aapt2 -> javac -> d8 -> apksigner.
# Needs: JDK 17, Android SDK platform 28 and build-tools 34.0.0, plus Python 3 (Pillow, numpy) and
# cwebp for the photo wallpapers. On macOS: brew install openjdk@17 webp; pip3 install pillow numpy.
#
#   ./build.sh                     build Fresco.apk
#   DEVICE=<ip>:5555 ./build.sh --install    build, install over ADB and rebind the accessibility service
#
# Wallpapers, one folder per style under assets/wallpapers/ (WallpaperStyle.java picks among them):
#   nature/    ../wallpapers/nature/out/     <slot>-<day>.webp and -blur.webp (28), made by photos.py
#   abstract/  ../wallpapers/abstract/out/   the same names (28), made by photos.py
#   space/     ../wallpapers/space/out/      the same names (28), committed
#   art/       ../wallpapers/art/out/        everyday/<slot>-<n> (20) and december/<slot>-<n> (8), committed
# Nature and Abstract are Pexels/Unsplash photos, which can't be redistributed as files, so the first
# build downloads and prepares them (../wallpapers/photos.py).
#
# Signing: set FRESCO_KEYSTORE, FRESCO_KEY_ALIAS and FRESCO_KEYSTORE_PASS to sign with your own key.
# Otherwise a local debug key is made in app/debug.keystore (never commit it). Android only installs
# an update signed with the same key as the installed app.
set -euo pipefail

cd "$(dirname "$0")"
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@17 ]]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
[[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-28/android.jar"
DEVICE="${DEVICE:-}"
OUT=build
WP=../wallpapers

# Fetch and prepare the photo sets the first time (or if any are missing).
for set in nature abstract; do
    if (( $(ls "$WP/$set/out"/*.webp 2>/dev/null | wc -l) < 56 )); then
        echo "wallpapers: preparing $set (downloads 28 photos the first time)"
        python3 "$WP/photos.py" "$set"
    fi
done

rm -rf "$OUT" && mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets/wallpapers"

# ---- assets: fonts, then the wallpaper sets ----
cp -R assets/. "$OUT/assets/"

# pack <style> <from> <names…>: copies each <name>.webp and its blur that exists; sets $packed and $absent.
pack() {
    local style=$1 from=$2 name
    shift 2
    packed=0
    absent=()
    for name in "$@"; do
        if [[ -f "$from/$name.webp" ]]; then
            mkdir -p "$OUT/assets/wallpapers/$style/${name:h}"
            cp "$from/$name.webp" "$OUT/assets/wallpapers/$style/$name.webp"
            # No blurred copy: the app makes one on the device (DESIGN.md §6.1).
            [[ -f "$from/$name-blur.webp" ]] && cp "$from/$name-blur.webp" "$OUT/assets/wallpapers/$style/$name-blur.webp"
            packed=$((packed + 1))
        else
            absent+=("$name")
        fi
    done
}

weekly=()
for slot in morning day evening night; do for day in mon tue wed thu fri sat sun; do weekly+=("$slot-$day"); done; done
art=()
for slot in morning day evening night; do
    for n in 1 2 3 4 5; do art+=("everyday/$slot-$n"); done
    for n in 1 2; do art+=("december/$slot-$n"); done
done

for style from in nature "$WP/nature/out" abstract "$WP/abstract/out" space "$WP/space/out" art "$WP/art/out"; do
    if [[ $style == art ]]; then pack art "$from" "${art[@]}"; else pack "$style" "$from" "${weekly[@]}"; fi
    if (( ${#absent} > 0 )); then echo "wallpapers: $style is missing ${absent[*]}" >&2; exit 1; fi
    echo "wallpapers: $style $packed of 28 packed"
done

# ---- resources, code, dex ----
"$BT/aapt2" compile --dir res -o "$OUT/compiled"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A "$OUT/assets" -0 webp -0 jpg -0 ttf \
    -o "$OUT/unsigned.apk" --java "$OUT/gen" "$OUT"/compiled/*.flat

javac --release 8 -Xlint:all,-options,-serial -classpath "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java') 2> >(grep -v "bootstrap classpath" >&2)
"$BT/d8" --release --min-api 25 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)

# ---- sign ----
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [[ -n "${FRESCO_KEYSTORE:-}" ]]; then
    "$BT/apksigner" sign --ks "$FRESCO_KEYSTORE" --ks-key-alias "${FRESCO_KEY_ALIAS:-fresco}" \
        --ks-pass env:FRESCO_KEYSTORE_PASS --out Fresco.apk "$OUT/aligned.apk"
else
    if [[ ! -f debug.keystore ]]; then
        keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias fresco \
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fresco debug" >/dev/null
    fi
    "$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out Fresco.apk "$OUT/aligned.apk"
fi
echo "Built $(pwd)/Fresco.apk ($(du -h Fresco.apk | cut -f1))"

if [[ "${1:-}" == "--install" ]]; then
    [[ -n "$DEVICE" ]] || { echo "Set DEVICE=<stick ip>:5555 to install" >&2; exit 1; }
    adb -s "$DEVICE" install -r Fresco.apk
    # Android often doesn't rebind an updated accessibility service, so cycle accessibility off and on.
    services="$(adb -s "$DEVICE" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    adb -s "$DEVICE" shell settings put secure enabled_accessibility_services '""'
    adb -s "$DEVICE" shell settings put secure accessibility_enabled 0
    sleep 2
    adb -s "$DEVICE" shell settings put secure enabled_accessibility_services "$services"
    adb -s "$DEVICE" shell settings put secure accessibility_enabled 1
    echo "Re-enabled accessibility services: $services"
fi
