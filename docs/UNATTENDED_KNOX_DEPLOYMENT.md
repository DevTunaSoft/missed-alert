# Unattended Samsung Knox deployment (zero crew interaction)

App package: `com.crewalert.missedalert`. Knox auto-run launcher: `com.crewalert.missedalert/.BootstrapActivity`. The bootstrap activity has no visible UI, initializes diagnostics, notification channel, watchdog and pending-alarm recovery, then finishes. `StatusActivity` is reserved for IT diagnosis and is not part of the crew launcher.

## Production target: completely unattended and hidden from crew kiosk

**Confirmed operational requirement (2026-11-11):** Missed Alert must start and remain operational without crew setup, opening the app, pressing a Start button, or periodically returning to the app. It must not be present as a selectable app on the crew-facing kiosk. It must still be **allowed to execute** in the background. Normal acknowledgment of a source notification is separate from starting the monitoring service.

### Knox Manage app assignment (pilot, then fleet)

- In-house app package: `com.crewalert.missedalert`
- Installation type: **Auto-installed**
- Automatically run app: **After installation and every app update**
- Managed configuration `headless_on_launch`: **true** (Knox display label **Launch invisibly in Knox kiosk**)
- `interval_seconds`: **60**
- `max_repeats`: **0** (unlimited until acknowledgment)
- `extra_packages`: leave empty unless IT verifies an additional source package is required
- Kiosk: allow the package to run **outside the kiosk foreground app list**, but do not show its icon. Never disable, suspend, force-stop, or block the package as a hiding mechanism.

### Knox Service Plugin and permissions

- Battery optimization allowlist: `com.crewalert.missedalert`
- Force Stop Blocklist: `com.crewalert.missedalert`
- Runtime `POST_NOTIFICATIONS`: grant through Android Enterprise profile (Android 13+)
- Special **Notification access**: grant to `com.crewalert.missedalert/com.crewalert.missedalert.MissedAlertListener` (KSP version-dependent input format; on supported newer KSP, package-only is accepted)
- Ensure the notification channel is audible, kiosk notification-bar policy does not suppress sound, notification volume is sufficient, and DND policy does not suppress the channel.

**Important Android limitation:** An ordinary third-party APK cannot grant itself notification-listener access or `POST_NOTIFICATIONS`. IT must apply these policies before crew handoff. Neither Knox auto-run nor the recovery watchdog can work around denied permissions. Some older Android/KSP combinations may require IT to enable notification access once during staging. Do not claim zero-touch provisioning until tested on the target firmware.

### Unattended acceptance evidence

1. After IT applies policy and launches once, log `knox_auto_run_bootstrap`, `post_granted=true`, and listener connection without a crew tap.
2. Trigger actual Traumasoft MDT, Google/Samsung SMS, and missed-call notifications and verify they enter the pending queue.
3. Verify audible repeat delivery while screen off, including after Doze and an ordinary reboot; document observed delay (60 seconds is a target, not guaranteed by Android).
4. Verify the recovery watchdog runs and listener reconnects without manual opening.
5. Verify crew kiosk shows no Missed Alert icon and does not suppress notification sounds.
6. Verify acknowledgment stops repeats without restarting the app.
7. Capture Android version, model, Knox Manage/KSP version, policy feedback, and diagnostic log export for each test model.

**Field failure recorded 2026-11-06:** Knox bootstrap and watchdog ran, but `post_granted=false`, `bootstrap_listener_permission_missing`, and `listener_bound=false`. Battery and exact-alarm privileges were present. See [GitHub issue #2](https://github.com/DevTunaSoft/missed-alert/issues/2). Status: **open until permission application and real-device audible delivery are verified**.

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
