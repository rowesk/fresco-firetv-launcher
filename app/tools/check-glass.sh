#!/bin/zsh
# Solves the glass and scrims (DESIGN.md §7) for every packed wallpaper with the app's own code, on the
# Mac. Run after build.sh. Needs Pillow; android.jar is only there so Wallpapers.java compiles and loads.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$HOME/Library/Android/sdk/platforms/android-28/android.jar"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
python3 tools/picture_stats.py build/assets/wallpapers > "$out/grids.txt"
javac -nowarn -d "$out" -classpath "$ANDROID_JAR" src/io/github/rowesk/fresco/{Wallpapers,WallpaperStyle,Prefs}.java tools/GlassCheck.java
java -cp "$out:$ANDROID_JAR" io.github.rowesk.fresco.GlassCheck "$out/grids.txt"
rm -rf "$out"
