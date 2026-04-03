#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run_ui_tests.sh
# Builds the app in debug mode (mock data, no agent), runs UIAutomator tests,
# and pulls screenshots to ./test-screenshots/
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADB="/Users/vijayakella/Library/Android/sdk/platform-tools/adb"
PACKAGE="com.example.a2ui.chat"
DEVICE_SCREENSHOT_DIR="/data/user/0/${PACKAGE}/files/screenshots"
LOCAL_SCREENSHOT_DIR="${SCRIPT_DIR}/test-screenshots"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  A2UI Chat App — UIAutomator Test Runner"
echo "═══════════════════════════════════════════════════════"
echo ""

# ── 1. Check emulator ──────────────────────────────────────
echo "▶ Checking ADB device..."
DEVICE=$("$ADB" devices | grep -E "emulator|device" | grep -v "List" | awk '{print $1}' | head -1)
if [ -z "$DEVICE" ]; then
  echo "✗ No ADB device found. Start an emulator first."
  exit 1
fi
echo "  Connected: $DEVICE"

# ── 2. Build debug APK ─────────────────────────────────────
echo ""
echo "▶ Building debug APK (USE_REAL_AGENT=false)..."
cd "$SCRIPT_DIR"
./gradlew :app:assembleDebug --no-daemon -q
echo "  ✓ Debug APK built"

# ── 3. Build test APK ─────────────────────────────────────
echo ""
echo "▶ Building instrumented test APK..."
./gradlew :app:assembleDebugAndroidTest --no-daemon -q
echo "  ✓ Test APK built"

# ── 4. Install both APKs ───────────────────────────────────
echo ""
echo "▶ Installing APKs on $DEVICE..."
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
echo "  ✓ APKs installed"

# ── 5. Clear previous screenshots ─────────────────────────
echo ""
echo "▶ Clearing previous screenshots on device..."
"$ADB" -s "$DEVICE" shell "run-as $PACKAGE rm -rf files/screenshots" 2>/dev/null || true
echo "  ✓ Cleared"

# ── 6. Run UIAutomator tests ───────────────────────────────
echo ""
echo "▶ Running UIAutomator tests..."
echo "  (This may take 60–120 seconds for both test runs)"
echo ""
"$ADB" -s "$DEVICE" shell am instrument -w \
  -e class "com.example.a2ui.chat.A2UIChatUiTest" \
  -e debug false \
  com.example.a2ui.chat.test/androidx.test.runner.AndroidJUnitRunner
echo ""

# ── 7. Pull screenshots ────────────────────────────────────
echo "▶ Pulling screenshots..."
mkdir -p "$LOCAL_SCREENSHOT_DIR"

SCREENSHOTS=$("$ADB" -s "$DEVICE" shell "run-as $PACKAGE ls files/screenshots" 2>/dev/null || echo "")
if [ -z "$SCREENSHOTS" ]; then
  echo "  ⚠ No screenshots found. Check test output above for errors."
else
  for SCREENSHOT in $SCREENSHOTS; do
    "$ADB" -s "$DEVICE" exec-out "run-as $PACKAGE cat files/screenshots/${SCREENSHOT}" \
      > "${LOCAL_SCREENSHOT_DIR}/${SCREENSHOT}"
    echo "  ✓ Saved: ${LOCAL_SCREENSHOT_DIR}/${SCREENSHOT}"
  done
fi

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  Done! Screenshots saved to: ${LOCAL_SCREENSHOT_DIR}/"
echo "═══════════════════════════════════════════════════════"
echo ""
