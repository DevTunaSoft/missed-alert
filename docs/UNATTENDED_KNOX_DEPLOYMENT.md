# Unattended Knox deployment and crew-free acceptance

IT must provision and authorize everything before handing a device to crews. No app status screen or permission prompt should be crew-facing.

1. Assign and silently install APK through Knox Manage; add it to kiosk Allowlisted Apps Settings as a hidden background app, not the crew launcher.
2. IT triggers Knox Manage run-app command to lift initial stopped state. A fresh installation cannot safely rely on BOOT_COMPLETED alone.
3. IT confirms notification listener special access for com.crewalert.missedalert/.MissedAlertListener. POST_NOTIFICATIONS is a different permission; it must be granted on Android 13+. The app cannot self-grant special notification access.
4. Confirm channel sound and importance, notification volume and DND, battery restrictions, exact alarm permission, and actual MDT/SMS package names.
5. On spare phones test boot, app update, listener disconnection/reconnection, process kill (not force-stop), screen-off 15+ minutes, Doze, and overnight listening without touching app.
6. Record physical audible results, timestamps, alarm delay, device model/Android/One UI/Knox policy, and logs. No simulated CI run proves real audio.
7. Do not mistake app force-stop for ordinary process death. Android may block receivers and cancel pending intents until the stopped state is lifted.

References:
https://docs.samsungknox.com/admin/knox-manage/kbas/kba-801-run-a-background-app-in-kiosk-mode/
https://developer.android.com/reference/android/service/notification/NotificationListenerService
https://developer.android.com/about/versions/15/behavior-changes-all