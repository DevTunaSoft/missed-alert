# Missed Alert: managed XCover Pro/A-series pilot
Target: XCover Pro (Android 10–13 depending on installed firmware), A14–A17 (installed device/OS matrix required). This APK is **not** a privileged system app; Knox manages normal app background execution.

Configure package `com.crewalert.missedalert` as Knox background allowlisted, battery-optimization allowlisted, force-stop-blocklisted; grant notification-listener access, POST_NOTIFICATIONS on Android 13+, and Alarms & Reminders on Android 12+. Ensure the source apps and this app are notification-allowlisted. Open or remotely launch the app once during enrollment. Verify effective settings, not just assigned policy.

Smoke-test the built APK on an isolated device, under actual kiosk policy. Check log progression: listener_connected → alert_tracked → alarm_scheduled → alarm_fired → reminder_posted, repeating until explicit acknowledgment. If no alert_tracked, check actual source package listed on IT status screen and add to Knox managed extra_packages. If inexact_fallback, 60-second delivery cannot be guaranteed. On Android 15+, test notification cooldown and muted/DND/speaker routing.

Test 5+ reminders each for SMS, MDT and missed call: awake, screen off, Doze, reboot, application upgrade, 24h idle, and simultaneous alerts. Validate expected timestamps, audible sound, acknowledgments, power usage. The 15-minute job is a safety net, **not** a 60-second timing guarantee. Force-stop may require IT recovery. Do not deploy to clinical production without actual device acceptance.
