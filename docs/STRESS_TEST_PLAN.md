# Missed Alert – stress test plan

Companion to HIDDEN_KIOSK_TEST_PLAN.md. Same rules: non-operational test phones, app hidden, never opened.
Every test is judged from the app's own log (`adb pull /sdcard/Android/data/com.crewalert.missedalert/files/logs`).
Nothing here has been run yet.

**Lab trick for generating load:** on the TEST phone only, set the managed config `extra_packages=com.android.shell`.
Notifications posted from adb then count as watched alerts, and `scripts/stress_burst.sh` can flood the phone.
(Verify the `cmd notification post` syntax on your Android build. Remove the setting afterwards.)

| # | Stress | How | Pass |
|---|--------|-----|------|
| S1 | Burst | `scripts/stress_burst.sh 100 0.2`, then 500, then 1000 | One combined reminder at a time, cadence still ~60 s, no crash/ANR, phone responsive; log shows `alert_tracked` for each |
| S2 | Churn | Same notification updated every second for 10 min (chatty app) | No growth in pending count, a single alarm (`dumpsys alarm`), no memory climb (`dumpsys meminfo com.crewalert.missedalert`) |
| S3 | Handle/arrive cycles | 200 cycles of: alert arrives -> handled in source app | No stuck reminder, no orphan alarm when idle, `pending=0` at the end |
| S4 | Soak | 72 h with one alert left unhandled, then a 21-day run with normal use | `late_ms` stays small, no missed cadence, battery and heat acceptable, log size stays bounded (2 files x 256 KB) |
| S5 | Reboot loop | 20 reboots, alert pending before each, app never opened | Listener re-binds every time (`listener_connected` after each `boot_or_update`); record time to first reminder |
| S6 | Process-kill loop | `adb shell am kill com.crewalert.missedalert` every 30 s for 30 min with an alert pending | Reminders continue; no duplicate or missing reminders |
| S7 | Doze cycles | `dumpsys deviceidle force-idle` / `unidle` 50 times with an alert pending | `late_ms` of `alarm_fired` stays small (alarm-clock alarm is Doze-exempt) |
| S8 | Clock jumps | Change time forward/back by hours, change timezone, toggle automatic time with an alert pending | `clock_changed` logged, alarm re-armed, cadence returns to ~60 s |
| S9 | Memory/thermal | 4 GB model, heavy MDT use + background load, hot phone | Listener stays bound; no kill |
| S10 | Pile-up | Leave 200 alerts unhandled for an hour | Single combined reminder, text stays readable, no storage growth beyond log cap |
| S11 | Low battery / battery saver | Battery saver on, <15 % | Cadence holds or the degradation is understood and documented |
| S12 | Permission removal | Revoke notification access, then POST_NOTIFICATIONS, while an alert is pending | Fails visibly in the log (`notifications_enabled=false`); Knox re-applies the grant |

Review the log for: gaps in `alarm_fired` timestamps, `late_ms` above 10 000, `listener_disconnected` without a
following `listener_connected`, `reminder_shown` with `notifications_enabled=false` or `channel_importance` below 4.
