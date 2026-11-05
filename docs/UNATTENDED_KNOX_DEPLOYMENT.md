# Unattended Samsung Knox deployment (zero crew interaction)

App package: `com.crewalert.missedalert`. Knox auto-run launcher: `com.crewalert.missedalert/.BootstrapActivity`. The bootstrap activity has no visible UI, initializes diagnostics, notification channel, watchdog and pending-alarm recovery, then finishes. `StatusActivity` is reserved for IT diagnosis and is not part of the crew launcher.

## Mandatory IT configuration before device handoff

1. **Install and automatically run:** assign the APK through Knox Manage. Under **Automatically run app**, select **After installation and every app update** (preferred), or **After installation** plus a managed Run app command after updates. On applicable Knox consoles this is also called Auto-run after Install. Android may keep newly installed apps in the stopped state until their first launch; without this step, boot broadcasts cannot be trusted to activate the app. Verify one `knox_auto_run_bootstrap` event.
2. **Battery optimization allowlist:** in Knox Service Plugin > Application management policies, add **com.crewalert.missedalert** to the **Battery optimization allowlist**. Confirm the exemption is in effect on each target OS/build; do not assume app permissions grant it.
3. **Force Stop Blocklist:** add **com.crewalert.missedalert** to **Force Stop Blocklist** to prevent crew/system policies from stopping its process where Knox supports the policy. Verify on real managed devices; it is not a guarantee against all firmware failures.
4. Keep the app allowed to execute in kiosk mode, even when the launcher icon is hidden. **Do not use Hide apps / Disable app / App execution blocklist** to hide the application: those settings can remove or disable its functionality. The default launcher must remain `BootstrapActivity`.
5. IT must arrange notification-listener access for `com.crewalert.missedalert/.MissedAlertListener` (special access), `POST_NOTIFICATIONS` (Android 13+), audible reminder channel, desired notification volume and DND, and exact-alarm permission when available. Knox cannot necessarily silently grant every special access permission on every model/Android variant: IT must resolve any one-time action before handing out the device.
6. Verify watched source packages and managed configuration (Traumasoft MDT, Google Messages, phone missed calls). Confirm a test alert fires, repeats, and is audible while screen is off, and stops after proper acknowledgment. Crew members never need to launch or configure Missed Alert.
7. After update, reboot, listener reconnection, and Doze testing, confirm logs, actual audible result, and no setup prompts. Keep the 8-day diagnostic log export accessible to IT.

## Required pilot evidence

Record device model, Android version, One UI, Knox firmware/policy version, APK SHA/signature, permission grants, launch event, boot/update transitions, reminder timings, observed audio, and final acknowledgment. Android can defer inexact alarms in Doze; 60-second delivery cannot be assumed under every configuration. Do not deploy fleet-wide until the actual models and policy combinations pass.

## Knox documentation

- https://docs.samsungknox.com/admin/knox-manage/new-console/manage-apps/assign-apps/
- https://docs.samsungknox.com/admin/knox-platform-for-enterprise/knox-service-plugin/policies.html
- https://docs.samsungknox.com/admin/knox-manage/new-console/manage-devices/send-device-commands/
