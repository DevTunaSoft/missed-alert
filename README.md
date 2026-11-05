# Missed Alert – repeating reminder for missed texts / MDT alerts

Re-alerts every 60 s (default notification tone + vibration) until the alert is acknowledged.
No UI interaction needed. Android 13+ (minSdk 33), Samsung A14–A17.

## How it works
1. `MissedAlertListener` (NotificationListenerService) sees every notification from watched apps:
   TraumaSoft MDT (`com.traumasoft.mdt` and `com.traumasoft.mdtv2`), Samsung Messages, Google Messages, AOSP MMS,
   plus any other packages you add via `extra_packages`.
2. An alert is **unacknowledged** until its notification is removed (opened, swiped, cleared) or the
   reminder is acknowledged.
3. While anything is unacknowledged, an exact Doze-proof alarm fires every 60 s and posts a
   high-importance reminder on a channel using the **device default notification sound**.
4. Acknowledge = tap the reminder (opens the source app), tap ACKNOWLEDGE, or swipe it away.
5. State survives process death; reboot clears it and the listener rebinds on its own.

## Build
Needs Android Studio, or JDK 17+ and Gradle 8.7+ with the Android SDK (platform 35):

    gradle assembleRelease      # sign it with your release keystore

Package: `com.crewalert.missedalert`. **Not yet compiled** – first build may need small fixes.

## Managed configuration (Knox Manage / KSP app config)
| Key | Type | Default | Purpose |
|---|---|---|---|
| `extra_packages` | string | empty | Comma-separated package names, e.g. the TraumaSoft MDT app |
| `interval_seconds` | int | 60 | Repeat interval (min 15) |
| `max_repeats` | int | 0 | 0 = until acknowledged |

Find the MDT package name: install on one phone, receive an MDT alert, open Missed Alert –
the "Apps that have posted notifications" list shows it.

## Knox deployment checklist (fully managed devices, Knox Service Plugin 24.03+)
1. Upload the signed APK as an in-house app; assign to the device group; auto-install.
   The app must be installed BEFORE the permission policies below can apply.
2. Managed config is optional: the MDT package is built in. Use `extra_packages` only to add other text apps.
3. Grant runtime permission `POST_NOTIFICATIONS` (permission policy: auto-grant).
4. Knox Service Plugin → Device-wide policies:
   - Enable device policy controls = True
   - Application management policies → Enable application management controls = True
   - **Battery optimization allowlist** = `com.crewalert.missedalert`
   - Enable permission controls = True, then add a Permission Controls entry:
     - Package/component: `com.crewalert.missedalert/com.crewalert.missedalert.MissedAlertListener`
     - Permissions: **Notification access** and **Alarms & Reminders**
   (Knox docs: "Grant special permissions for an app", KBA-1261. Notification access needs the
   `package/component` format. Fully managed devices only.)
5. Samsung extras: turn off "Put unused apps to sleep" / "Auto-disable unused apps" fleet-wide
   (Battery → Background usage limits), or add the app to "Never sleeping apps". Verify during pilot.
6. Fallback if the policy doesn't take: one-time per phone over USB debugging:
   `adb shell cmd notification allow_listener com.crewalert.missedalert/com.crewalert.missedalert.MissedAlertListener`
7. Make sure the MDT app and the SMS app have notifications enabled, otherwise there is
   nothing to repeat.

## Why setAlarmClock()
Normal "exact" alarms are throttled to roughly one per 9 minutes per app once the phone is in
deep Doze (stationary, screen off, unplugged). `setAlarmClock()` is exempt, so reminders stay at
60 s. Side effect: Android shows a small alarm icon in the status bar while an alert is pending.

## Test
- Open the app → "Send test alert": tone every 60 s until acknowledged.
- Real test: lock the phone, send a text, ignore it – expect reminders at ~60 s intervals.
- Reboot test; also leave a phone idle 30+ min to confirm Doze doesn't delay reminders.

## Limits to know
- The tone follows device volume / silent / DND. A phone on silent stays silent.
- If the source app clears its own notification, that counts as acknowledged.
