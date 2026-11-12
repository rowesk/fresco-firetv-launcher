#!/bin/zsh
# Draws every dock mark into a contact sheet on the Mac with the app's own data and code (MarkArt reads
# assets/marks as the app does), at rest on a painting and focused on milk glass. Needs Pillow, for the
# painting behind them.
#
#   tools/preview-marks.sh [out.png]           the sheet (default: build/marks-preview.png)
#   tools/preview-marks.sh --draw [out.png]    first redraw the original marks (tools/DrawMarks.java)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$HOME/Library/Android/sdk/platforms/android-28/android.jar"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
if [[ "${1:-}" == "--draw" ]]; then
    shift
    javac -d "$out" tools/DrawMarks.java
    java -cp "$out" DrawMarks assets/marks
fi
sheet="${1:-build/marks-preview.png}"
python3 -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").resize((1920, 1080)).save(sys.argv[2])' \
    ../wallpapers/art/out/everyday/day-3.webp "$out/bg.png"
# android.jar is only there so Marks.java and Ui.java compile; the tool uses their constants, not Android.
javac -nowarn -d "$out" -classpath "$ANDROID_JAR" src/io/github/rowesk/fresco/{MarkArt,Marks,Ui}.java tools/MarksPreview.java 2>&1 \
    | grep -v "bootstrap classpath" || true
java -Djava.awt.headless=true -cp "$out:$ANDROID_JAR" io.github.rowesk.fresco.MarksPreview assets "$out/bg.png" "$sheet"
