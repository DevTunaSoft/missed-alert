# Knox / screen-off / Doze pilot acceptance plan

This is a test procedure, not evidence that Samsung validation has passed.

1. For each XCover Pro, A14, A15, A16 and A17 device/OS/Knox policy variant record model, build, app commit, exact alarm permission, notification access, battery policy, channel importance and sound, Do Not Disturb state.
2. Inject real Traumasoft MDT, Google Messages/SMS, and missed-call notifications on a non-production phone. Confirm tracked, scheduled, fired, and posted events and audibly verify each reminder without touching the screen.
3. Repeat with screen off for at least 15 minutes and with an overnight unattended session; capture actual intervals and audible results.
4. On an authorized spare test device only, run adb shell dumpsys deviceidle force-idle; check three audible reminder cycles and capture diagnostic logs; run adb shell dumpsys deviceidle unforce afterward.
5. Test listener reconnect, process death without force-stop, reboot, notification burst, single acknowledgment, acknowledge-all, fresh notification after acknowledgment, and revoked exact alarm permission.
6. Evaluate the alarm-clock path separately from inexact fallback, where 60-second delivery is NOT guaranteed under Doze.
7. Export logs with timestamps and redacted identifying data. Require observed audio, not merely an alarm_fired log event. Mark unobserved outcomes inconclusive.

## Sources
https://developer.android.com/develop/background-work/services/alarms
https://developer.android.com/training/monitoring-device-state/doze-standby
https://developer.android.com/about/versions/14/changes/schedule-exact-alarms