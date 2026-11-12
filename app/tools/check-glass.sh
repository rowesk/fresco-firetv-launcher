#!/bin/zsh
# Solves the glass and scrims (DESIGN.md §7) for every packed wallpaper with the app's own code, on the
# Mac. Run after build.sh. Needs Pillow; android.jar is only there so Wallpapers.java compiles and loads.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$HOME/Library/Android/sdk/platforms/android-28/android.jar"
out=$(mktemp -d)
# The app's grid: 32x18 cell means of the 480x270 blur (15x15 px each, so a box resize is exact).
python3 - build/assets/wallpapers > "$out/grids.txt" <<'PY'
import os, sys
from PIL import Image
root = sys.argv[1]
for d, _, files in sorted(os.walk(root)):
    for f in sorted(files):
        if f.endswith('-blur.webp') or f.endswith('-blur.jpg'):
            im = Image.open(os.path.join(d, f)).convert('RGB').resize((32, 18), Image.BOX)
            name = os.path.relpath(os.path.join(d, f), root).rsplit('-blur', 1)[0]
            print(name, ' '.join(str(v) for px in im.getdata() for v in px))
PY
javac -nowarn -d "$out" -classpath "$ANDROID_JAR" src/io/github/rowesk/fresco/{Wallpapers,WallpaperStyle,Prefs}.java tools/GlassCheck.java 2>&1 | grep -v "bootstrap classpath" || true
java -cp "$out:$ANDROID_JAR" io.github.rowesk.fresco.GlassCheck "$out/grids.txt"
rm -rf "$out"
