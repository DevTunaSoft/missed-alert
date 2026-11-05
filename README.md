# Missed Alert – repeating reminder for missed texts, MDT alerts and missed calls

**Zero-touch:** nobody opens or configures the app. Knox installs it and grants every permission by policy.

Re-alerts every 60 s (default notification tone + vibration) until the alert is acknowledged.
No UI interaction needed. Android 13+ (minSdk 33), Samsung A14–A17.

## How it works
1. `MissedAlertListener` (NotificationListenerService) sees every notification from watched apps:
   TraumaSoft MDT (`com.traumasoft.mdt` and `com.traumasoft.mdtv2`), Samsung Messages, Google Messages, AOSP MMS,
   plus any other packages you add via `extra_packages`.
   **Missed phone calls** are detected from the dialer / Telecom missed-call notification
   (`com.android.server.telecom` channel `TelecomMissedCalls`, Samsung incallui/dialer, Google dialer).
   Incoming and ongoing calls are never tracked.
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

## Knox Manage kiosk (lessons from MMT Volume Guard's pilot notes)
Your phones run a Knox Manage kiosk, which can block things a normal Android install allows:
1. Add `com.crewalert.missedalert` as a **background app** in the kiosk's *Allowlisted Apps Settings /
   Apps outside kiosk* – not on the crews' visible app list.
2. Assign the app with **Auto-install** and **Automatically run app: after installation and every
   app update**, and send **Run app** once. A never-launched app gets no boot events.
3. Add it to the **Force Stop blocklist** and the **battery optimization allowlist**. A force-stopped
   app loses its alarms and listener until someone opens it.
4. **Kiosk notification policy:** Samsung documents that kiosks with Notification Bar = *Disallow* play
   no notification sounds (KBA-1686). The reminder is a notification, so it is silent in that case.
   The notification policy must allow this app's (and the SMS/MDT apps') notifications.
5. Crews acknowledge by opening the MDT / text / phone app, which clears its own notification; the
   reminder then stops by itself. If the MDT app does NOT clear its notification when handled, the
   reminder keeps repeating – check this in the pilot.
6. Also test Samsung **Separate app sound**, **Mute all sounds**, Do Not Disturb and a Bluetooth/wired
   route: volume at max does not guarantee the speaker is used. Can't be verified from a build.

## Use together with MMT Volume Guard
Volume Guard keeps the notification and ring streams at max and forces the ringer to Normal; Missed
Alert supplies the repeating. They don't conflict (different packages, no shared state). Keep
`inc_notification`, `inc_ring` = true and `pct_*` = 100 in Volume Guard's managed config. Gap neither
app closes: Do Not Disturb (needs policy access Knox Permission Controls doesn't list) – confirm
crews can't enable it in the kiosk.

## Android versions
Built for Android 13–17 (minSdk 33, targetSdk 36). Android 17 behavior-change pages were reviewed:
nothing found affecting notification listeners or alarm-clock alarms. The tone is played by the
system through the notification channel (not by the app), so Android 17's background-audio
restrictions don't apply. Android 15+ "Notification cooldown" can lower the volume of repeated
notifications from one app on some builds – check Settings → Notifications → Notification cooldown
(set to off via Knox "deep settings" if present) and confirm volume during the pilot.

## Why not a "system app"?
Not needed. Knox Service Plugin Permission Controls grants the same special access
(Notification access, Alarms & Reminders) to a normally installed app, and the battery allowlist
covers background survival. Making it a true system/priv-app would require signing into the firmware.

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
