#!/usr/bin/env bash
# Installs (or updates, or removes) Fresco on a Fire TV over Wi-Fi ADB.
#
#   ./install.sh 192.168.1.50                 download the latest release and install it
#   ./install.sh 192.168.1.50 Fresco.apk      install a local APK instead
#   ./install.sh --uninstall 192.168.1.50     put everything back the way it was
#
# Before running it, on the Fire TV: Settings > My Fire TV > Developer options > ADB debugging: ON.
# (No Developer options? Settings > My Fire TV > About, then press Select on the device name 7 times.)
# The TV's IP address is under Settings > My Fire TV > About > Network.
#
# What it changes on the Fire TV, and nothing else:
#   - installs the app io.github.rowesk.fresco
#   - adds Fresco to the enabled accessibility services, keeping any that are already on
# Amazon's own launcher stays installed and untouched; Fresco only steps in front of it.
set -euo pipefail

PKG=io.github.rowesk.fresco
SERVICE="$PKG/$PKG.FrescoService"
RELEASE_URL="https://github.com/rowesk/fresco-firetv-launcher/releases/latest/download/Fresco.apk"

say() { printf '\033[1m%s\033[0m\n' "$*"; }
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

uninstall=0
if [[ "${1:-}" == "--uninstall" ]]; then uninstall=1; shift; fi
[[ $# -ge 1 ]] || { sed -n '2,12p' "$0"; exit 1; }
host="$1"
[[ "$host" == *:* ]] || host="$host:5555"
apk="${2:-}"

command -v adb >/dev/null || die "adb isn't installed. macOS: brew install android-platform-tools. Linux: install your distro's android-tools (adb) package."

# ---- connect. Fire TVs drop Wi-Fi ADB while their Wi-Fi naps, so keep trying for a bit. ----
say "Connecting to $host"
state=""
for _ in $(seq 1 20); do
    adb connect "$host" >/dev/null 2>&1 || true
    state="$(adb -s "$host" get-state 2>&1 || true)"
    case "$state" in
        device) break ;;
        *unauthorized*) echo "  On the TV, choose 'Allow' on the USB debugging prompt (tick 'Always allow from this computer')." ;;
        *) echo "  Not answering yet; is the TV on and on the same network? Retrying." ;;
    esac
    sleep 3
done
[[ "$state" == device ]] || die "couldn't connect to $host. Check the IP address and that ADB debugging is on."
a() { adb -s "$host" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }

if (( uninstall )); then
    services="$(sh_ settings get secure enabled_accessibility_services)"
    rest="$(printf '%s' "$services" | tr ':' '\n' | grep -v "^$SERVICE\$" | grep -v '^null$' | paste -sd: - || true)"
    if [[ -n "$rest" ]]; then a shell settings put secure enabled_accessibility_services "$rest"
    else a shell settings delete secure enabled_accessibility_services >/dev/null; a shell settings put secure accessibility_enabled 0; fi
    a uninstall "$PKG" >/dev/null 2>&1 || true
    a shell input keyevent KEYCODE_HOME
    say "Fresco is removed. Home goes to Amazon's home screen again."
    exit 0
fi

# ---- check it's a Fire TV that can run it ----
model="$(sh_ getprop ro.product.model)"
maker="$(sh_ getprop ro.product.manufacturer)"
sdk="$(sh_ getprop ro.build.version.sdk)"
fireos="$(sh_ getprop ro.build.version.name)"
echo "  $maker $model, Android API $sdk ${fireos:+($fireos)}"
[[ "$maker" == Amazon ]] || echo "  Warning: this isn't an Amazon device. Fresco is made for Fire OS; carrying on anyway."
[[ "$sdk" =~ ^[0-9]+$ && "$sdk" -ge 25 ]] || die "Fresco needs Fire OS 6 or later (Android API 25+); this device reports API '$sdk'."

# ---- get the APK ----
if [[ -z "$apk" ]]; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    apk="$tmp/Fresco.apk"
    say "Downloading the latest release"
    curl -fL --progress-bar -o "$apk" "$RELEASE_URL" || die "download failed: $RELEASE_URL"
    if curl -fsL -o "$tmp/SHA256SUMS" "${RELEASE_URL%/*}/SHA256SUMS" 2>/dev/null; then
        want="$(awk '/Fresco.apk/ {print $1}' "$tmp/SHA256SUMS")"
        got="$( (shasum -a 256 "$apk" 2>/dev/null || sha256sum "$apk") | awk '{print $1}')"
        [[ -z "$want" || "$want" == "$got" ]] || die "checksum mismatch for the downloaded APK"
        [[ -n "$want" ]] && echo "  Checksum OK"
    fi
fi
[[ -f "$apk" ]] || die "no APK at $apk"

# ---- install ----
say "Installing Fresco"
if ! out="$(a install -r "$apk" 2>&1)"; then
    echo "$out" >&2
    if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out"; then
        die "the installed Fresco was signed with a different key. Run ./install.sh --uninstall $1 first, then install again."
    fi
    die "install failed"
fi

# ---- turn on the accessibility service (it's what catches the Home button) ----
say "Turning on Fresco's accessibility service"
services="$(sh_ settings get secure enabled_accessibility_services)"
[[ "$services" == null ]] && services=""
others="$(printf '%s' "$services" | tr ':' '\n' | grep -v "^$SERVICE\$" | grep -v '^$' | paste -sd: - || true)"
# Android often doesn't rebind an updated service, so switch ours off and on again.
a shell settings put secure enabled_accessibility_services "${others:-\"\"}"
sleep 2
a shell settings put secure enabled_accessibility_services "${others:+$others:}$SERVICE"
a shell settings put secure accessibility_enabled 1

a shell am start -n "$PKG/.HomeActivity" >/dev/null
say "Done. Fresco is on the TV."
cat <<EOF

Two more steps on the TV, with the remote:
  1. Pin Fresco to Amazon's home row: press the Apps button (three squares), highlight Fresco,
     press the Menu button (three lines) and choose "Move to front". Home is instant when the tile
     is on the home row; without it Home still works but can take up to 5 seconds.
  2. Open Fresco's settings (the cog, top right) to choose your dock apps, wallpapers,
     remote buttons and the bedtime tint.

Optional, for Stremio users: DEVICE=$host app/tools/stremio-login.sh
To undo everything: ./install.sh --uninstall $1
EOF
