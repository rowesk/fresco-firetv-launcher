#!/bin/zsh
# Signs Fresco in to Stremio. The Mac logs in; only the login token goes to the stick.
#
#   DEVICE=192.168.1.50:5555 app/tools/stremio-login.sh                  # asks for your email and password
#   DEVICE=… app/tools/stremio-login.sh ~/secrets/stremio                # a file with STREMIO_EMAIL=… and STREMIO_PASSWORD=… lines
#   DEVICE=… STREMIO_EMAIL=… STREMIO_PASSWORD=… app/tools/stremio-login.sh
#   DEVICE=… app/tools/stremio-login.sh --logout
#
# The password is never echoed, never put on a command line and never written anywhere: zsh's
# builtin printf pipes it to python, which builds the JSON body that curl reads from stdin and
# sends to api.strem.io over HTTPS. The token is dropped in the app's private files with run-as
# (the app is debuggable for this) and the app moves it into its settings file on the next sync,
# which the broadcast at the end asks for.
set -euo pipefail

DEVICE="${DEVICE:-}"
[[ -n "$DEVICE" ]] || { echo "Set DEVICE=<stick ip>:5555" >&2; exit 1; }
PKG=io.github.rowesk.fresco
API=https://api.strem.io/api/login

if [[ "${1:-}" == "--logout" ]]; then
    token=""
else
    if [[ -n "${1:-}" ]]; then
        # Read KEY=value lines without sourcing the file.
        while IFS= read -r line || [[ -n "$line" ]]; do
            case "$line" in
                STREMIO_EMAIL=*) STREMIO_EMAIL="${line#STREMIO_EMAIL=}" ;;
                STREMIO_PASSWORD=*) STREMIO_PASSWORD="${line#STREMIO_PASSWORD=}" ;;
            esac
        done < "$1"
        STREMIO_EMAIL="${${STREMIO_EMAIL:-}//[\"\']/}"
        STREMIO_PASSWORD="${STREMIO_PASSWORD:-}"
        [[ "$STREMIO_PASSWORD" == \"*\" || "$STREMIO_PASSWORD" == \'*\' ]] && STREMIO_PASSWORD="${STREMIO_PASSWORD:1:-1}"
    fi
    if [[ -t 0 ]]; then
        [[ -n "${STREMIO_EMAIL:-}" ]] || read -r "STREMIO_EMAIL?Stremio email: "
        if [[ -z "${STREMIO_PASSWORD:-}" ]]; then read -rs "STREMIO_PASSWORD?Stremio password (not shown): "; echo; fi
    fi
    if [[ -z "${STREMIO_EMAIL:-}" || -z "${STREMIO_PASSWORD:-}" ]]; then
        echo "Set STREMIO_EMAIL and STREMIO_PASSWORD, or pass a file that has them." >&2
        exit 1
    fi

    # Login body as stremio-core sends it: {"type":"Login","email":…,"password":…,"facebook":false}.
    response="$(printf '%s\0%s' "$STREMIO_EMAIL" "$STREMIO_PASSWORD" \
        | python3 -c 'import json,sys; e,p=sys.stdin.read().split("\0",1); json.dump({"type":"Login","email":e,"password":p,"facebook":False},sys.stdout)' \
        | curl -sS --max-time 20 -H 'Content-Type: application/json' --data-binary @- "$API")"
    unset STREMIO_PASSWORD

    token="$(printf '%s' "$response" | python3 -c '
import json, sys
try:
    r = json.load(sys.stdin)
except ValueError:
    sys.exit("Stremio sent something unexpected; nothing was changed on the stick.")
if r.get("error"):
    e = r["error"]
    sys.exit("Stremio refused the login: " + (e.get("message") if isinstance(e, dict) else str(e)))
key = (r.get("result") or {}).get("authKey")
if not key:
    sys.exit("No token in Stremio'"'"'s answer; nothing was changed on the stick.")
print(key)
')"
fi

adb connect "$DEVICE" >/dev/null 2>&1 || true
# Fire OS 7's adbd has no exec-in (the file arrived empty), so stage the token in /data/local/tmp for
# run-as to copy, then delete both copies straight away.
drop="$(mktemp)"
printf '%s' "$token" \
    | python3 -c 'import json,sys; t=sys.stdin.read().strip(); print(json.dumps({"authKey": t or None}))' > "$drop"
adb -s "$DEVICE" push "$drop" /data/local/tmp/stremio-auth.json >/dev/null
rm -f "$drop"
adb -s "$DEVICE" shell "chmod 644 /data/local/tmp/stremio-auth.json && run-as $PKG sh -c 'mkdir -p files && cat /data/local/tmp/stremio-auth.json > files/stremio-auth.json'; rm -f /data/local/tmp/stremio-auth.json"
adb -s "$DEVICE" shell am broadcast -a io.github.rowesk.fresco.SYNC -n "$PKG/.SyncReceiver" >/dev/null

if [[ -z "$token" ]]; then
    echo "Signed out. The token has been removed from the stick."
else
    echo "Signed in as $STREMIO_EMAIL. The stick has the token and is syncing Continue watching."
fi
