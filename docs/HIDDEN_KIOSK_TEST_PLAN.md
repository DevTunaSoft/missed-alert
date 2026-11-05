# Missed Alert – hidden / kiosk test plan

Goal: prove that, with the app **hidden in the Knox kiosk and never opened by a crew member**, every missed
text, MDT alert and missed call repeats about every 60 s with the default tone until it is handled.
Run on **non-operational** test phones first. Nothing here has been verified on hardware yet.

**Test phones:** one each of A14, A15, A16, A17 (note exact SM-Axxx, Android + One UI version).
**Setup:** signed APK, Knox kiosk with the app as a hidden background app, Auto-run after install,
Run app sent once, Force Stop blocklist, battery allowlist, KSP Permission Controls
(Notification access + Alarms & Reminders), POST_NOTIFICATIONS granted, MMT Volume Guard installed.
Do NOT open Missed Alert on the phone during tests H2–H12.

PASS = reminders arrive every ~60 s (±10 s) with the default notification tone + vibration, and stop within
~10 s of the item being handled. Record timestamps.

| # | Test | Steps | Expected |
|---|------|-------|----------|
| H1 | Hidden | After install + Run app, look at home/launcher/recents | No icon, no screen shown, nothing crew-visible |
| H2 | Zero-touch permissions | IT checks (outside kiosk or adb): listener enabled, notifications allowed, battery unrestricted, exact alarm allowed | All granted by policy, nobody tapped anything |
| H3 | Text message | Send an SMS, screen off + locked, ignore it | Reminders every ~60 s until the message is opened; stop after opening |
| H4 | MDT alert | Trigger a real TraumaSoft alert, ignore it | Repeats every ~60 s. Open the MDT app: **does the reminder stop?** (if not, the MDT app does not clear its notification – report) |
| H5 | Missed call | Call the phone, let it ring out | Repeats after the call is missed; stops after opening the call log. Ringing/ongoing call must NOT create reminders |
| H6 | Several at once | Leave a text + MDT alert + missed call unhandled | One combined reminder listing them (not a flood); stops only when all are handled |
| H7 | Reboot, unattended | Reboot with an unhandled alert pending, do not touch the phone, then send a new text | Reminders work after boot with no one opening the app. Record time from boot to first reminder |
| H8 | Deep idle | Screen off, unplugged, stationary 60 min, then send a text. Quick version: `adb shell dumpsys deviceidle force-idle` | Cadence stays ~60 s, not ~9 min |
| H9 | Samsung sleep | Leave the phone 24–48 h untouched, then send a text. Check the app is not in Sleeping / Deep sleeping apps | Reminders still work |
| H10 | Process kill | `adb shell am kill com.crewalert.missedalert` while an alert is pending | Reminders resume / continue. Also confirm Knox blocks force-stop |
| H11 | Kiosk sound policy | Run H3 with kiosk Notification Bar = Disallow, then = Allow | Know which one is audible (Samsung KBA-1686). Reminder must be audible in the deployed policy |
| H12 | Volume Guard together | Turn volume down / set Silent / Vibrate, then send a text | Volume Guard restores; reminder is full volume. Compare volume of 1st vs 5th repeat (Android 15+ notification cooldown) |
| H13 | Audibility traps | Repeat H3 with: Do Not Disturb on, Samsung "Separate app sound", "Mute all sounds", Bluetooth/wired headset | Record which silence the reminder; crews must not be able to enable these in the kiosk |
| H14 | Burst | Send 50 texts quickly | One reminder, no crash, no notification flood, phone stays responsive |
| H15 | Battery | 24 h on a normal shift pattern with and without pending alerts | Battery drain acceptable; no thermal warning |
| H16 | Update | Push a newer signed build through Knox | Updates in place; permissions, listener access and battery exemption survive |

## Evidence to record
Model + Android + One UI, Knox profile type, for each test: PASS/FAIL, reminder timestamps (screen recording
or `adb logcat -s` output), and the output of:

    adb shell dumpsys notification | grep -i -A3 "listeners"
    adb shell dumpsys deviceidle whitelist | grep -i missedalert
    adb shell dumpsys alarm | grep -i -B2 -A6 missedalert
    adb shell dumpsys package com.crewalert.missedalert | grep -i -E "stopped|firstInstall"

The app keeps its own rolling event log (no message text, titles, names or numbers - only packages, channel
ids, counts, timings):

    adb pull /sdcard/Android/data/com.crewalert.missedalert/files/logs

Events: listener_connected / listener_disconnected, alert_tracked, skipped (with reason), alert_removed
(Android removal reason code, e.g. 1 = clicked, 2 = swiped, 8 = cancelled by app), alarm_scheduled,
alarm_fired (`late_ms` = how late versus due - the Doze evidence), reminder_shown
(`notifications_enabled`, `channel_importance`: 4 = high, anything lower will be quieter),
acknowledged (source), pruned, boot_or_update, clock_changed.
