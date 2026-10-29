#!/usr/bin/env bash
# Consnow 1.1.0 — automatable checks from the release test plan.
# Usage:
#   ./scripts/release-checks.sh device          # phone over USB (D2, S5, S8-lite, D3)
#   TOKEN=<test acct jwt> STRANGER_ID=<uuid of a user NOT connected to it> \
#     ./scripts/release-checks.sh api           # B1-B8 against the API
set -u
PKG=com.Consnow
API=${API:-https://consnow-backend-production.up.railway.app}
pass=0; fail=0
ok()  { echo "  PASS  $1"; pass=$((pass+1)); }
bad() { echo "  FAIL  $1"; fail=$((fail+1)); }
svc_running() { adb shell dumpsys activity services "$PKG" | grep -q "StepCounterService"; }

device() {
  echo "== Device =="
  [ "$(adb get-state 2>/dev/null)" = "device" ] || { echo "No phone found (adb devices). Enable USB debugging and accept the prompt."; exit 1; }
  echo "  $(adb shell getprop ro.product.manufacturer) $(adb shell getprop ro.product.model), Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
  ver=$(adb shell dumpsys package $PKG | grep -m1 versionName | tr -d ' ')
  code=$(adb shell dumpsys package $PKG | grep -m1 -o 'versionCode=[0-9]*')
  echo "  Installed: $ver $code"
  adb shell dumpsys package $PKG | grep -q "DEBUGGABLE" && bad "Installed build is DEBUG — install the release build" || ok "Release (non-debuggable) build installed"

  echo "== D2: permissions =="
  perms=$(adb shell dumpsys package $PKG)
  for p in POST_NOTIFICATIONS ACTIVITY_RECOGNITION; do
    echo "$perms" | grep -q "android.permission.$p: granted=true" && ok "$p granted" || bad "$p not granted"
  done

  echo "== Service + notification =="
  svc_running && ok "StepCounterService running" || bad "StepCounterService NOT running"
  adb shell dumpsys notification --noredact | grep -q "pkg=$PKG" && ok "Consnow notification visible" || bad "No Consnow notification"

  echo "== S5: kill app, service restarts =="
  adb shell am kill $PKG; sleep 20
  svc_running && ok "Service back 20 s after am kill" || bad "Service not back 20 s after am kill"

  echo "== D3 / crash scan (last 5 min of logcat) =="
  adb logcat -d -t "$(date -v-5M '+%m-%d %H:%M:%S.000')" > /tmp/consnow-logcat.txt 2>/dev/null || adb logcat -d > /tmp/consnow-logcat.txt
  grep -q "FATAL EXCEPTION.*" /tmp/consnow-logcat.txt && grep -A3 "FATAL EXCEPTION" /tmp/consnow-logcat.txt | grep -q "$PKG" && bad "Crash found (see /tmp/consnow-logcat.txt)" || ok "No Consnow crash in logcat"
  grep -q "SecurityException.*$PKG\|$PKG.*SecurityException" /tmp/consnow-logcat.txt && bad "SecurityException (permission) in logcat" || ok "No SecurityException"
  grep -q "StepSync" /tmp/consnow-logcat.txt && ok "StepSync log lines present" || echo "  INFO  No StepSync lines yet (walk a few steps and re-run)"
}

api() {
  : "${TOKEN:?set TOKEN to a TEST account token}"
  echo "== API abuse tests against $API =="
  put() { curl -s -o /tmp/b.json -w "%{http_code}" -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" ${1:+-d "$1"} "$API/activity/unlock"; }
  c=$(put '{"occurredAt":1830000000000}'); year=$(grep -o '"unlockedAt":"[0-9]\{4\}' /tmp/b.json | tail -c5)
  [ "$c" = 200 ] && [ "$year" = "$(date +%Y)" ] && ok "B1 future time clamped ($year)" || bad "B1 code=$c year=$year"
  c=$(put '{"occurredAt":-1}'); grep -q '"unlockedAt":"19' /tmp/b.json && bad "B2 stored a 19xx date" || { [ "$c" != 500 ] && ok "B2 -1 handled ($c)" || bad "B2 500"; }
  c=$(put '{"occurredAt":"hello"}'); [ "$c" != 500 ] && ok "B3 garbage handled ($c)" || bad "B3 returned 500"
  c=$(put ''); [ "$c" = 200 ] && ok "B4 no body -> 200" || bad "B4 code=$c"
  c=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$API/activity/unlock"); [ "$c" = 401 ] && ok "B5 no token -> 401" || bad "B5 code=$c"
  c=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}x" "$API/activity/me"); [ "$c" = 401 ] && ok "B8 edited token -> 401" || bad "B8 code=$c"
  if [ -n "${STRANGER_ID:-}" ]; then
    c=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$API/activity/peer/$STRANGER_ID"); [[ "$c" =~ ^40[34]$ ]] && ok "B6 stranger activity blocked ($c)" || bad "B6 PRIVACY: stranger activity returned $c — STOP RELEASE"
    c=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$API/steps/user/$STRANGER_ID?date=$(date +%F)"); [[ "$c" =~ ^40[34]$ ]] && ok "B7 stranger steps blocked ($c)" || bad "B7 PRIVACY: stranger steps returned $c — STOP RELEASE"
  else echo "  SKIP  B6/B7 (set STRANGER_ID)"; fi
}

case "${1:-}" in device) device;; api) api;; *) echo "usage: $0 device|api"; exit 2;; esac
echo; echo "Result: $pass passed, $fail failed"; [ $fail -eq 0 ]
