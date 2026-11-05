#!/usr/bin/env bash
# LAB ONLY - flood a TEST phone with notifications to stress Missed Alert.
# Needs: adb, and the test phone configured with managed config  extra_packages=com.android.shell
# so that notifications posted from the adb shell count as watched alerts. Remove that setting afterwards.
#
# Usage: scripts/stress_burst.sh <count> <delay_seconds>
#   e.g. scripts/stress_burst.sh 100 0.2
# Clear the notifications afterwards (swipe "clear all", or open them) to end the test.
set -euo pipefail
N="${1:-100}"
DELAY="${2:-0.2}"
for i in $(seq 1 "$N"); do
  adb shell cmd notification post -S bigtext -t "Stress $i" "stress_$i" "Stress test notification $i" >/dev/null
  sleep "$DELAY"
done
echo "Posted $N notifications. Pull the log: adb pull /sdcard/Android/data/com.crewalert.missedalert/files/logs"
