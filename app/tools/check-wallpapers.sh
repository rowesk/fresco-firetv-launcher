#!/bin/zsh
# Checks the wallpaper rotation (WallpaperStyle.java) on the Mac, no device needed.
# Run after build.sh, so "shows" reflects which sets this build packed.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
out=$(mktemp -d)
javac -d "$out" src/io/github/rowesk/fresco/WallpaperStyle.java tools/WallpaperStyleCheck.java
java -cp "$out" io.github.rowesk.fresco.WallpaperStyleCheck build/assets/wallpapers
rm -rf "$out"
