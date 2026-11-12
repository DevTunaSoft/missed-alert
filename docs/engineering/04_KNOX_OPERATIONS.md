# 04 — Samsung Knox Deployment and IT Operations

## Goal
Deploy a background reminder application on managed Samsung Android phones, without placing an app icon in the crew kiosk or requiring crews to open it. Preserve administrative diagnostics via Knox Remote Support.

## Preflight
Record device model, Android/One UI version, Knox Manage agent/policy, app versionCode/versionName, APK signing certificate, battery and notification policies, monitored apps and their package names. Confirm the actual package installed is `com.crewalert.missedalert` or the current manifest value. Verify no older conflicting app installation or signing mismatch.

## Deployment checklist
1. Upload the signed APK to the appropriate Knox app distribution policy. Verify signature continuity with installed versions.
2. Enable app installation and auto-run after install when supported by the actual Knox policy.
3. Configure managed restrictions: headless launch, monitored packages and repeat interval. Confirm policy values are applied on device.
4. Keep the reminder application out of the crew-facing multi-app kiosk launcher while allowing its background components to run.
5. Enable notification listener access and notification posting permission through supported enterprise mechanisms. Confirm status on device; never assume an administrative checkbox succeeded.
6. Allowlist relevant battery optimization restrictions and block force stop where policy supports it.
7. Ensure reminder channel has sound/vibration and appropriate importance; verify system alert volume and DND interaction.
8. Restart the device and verify listener binding, pending-state recovery, and actual repeat behavior.
9. Run a controlled SMS and TraumaSoft MDT test, wait through several 60-second cycles, acknowledge, and confirm repeats stop.
10. Use Knox Remote Support to inspect IT diagnostics and export the ZIP. Check that files are readable, dated, bounded, and contain no sensitive message content.

## Troubleshooting decision tree
**No event detected:** Check installed source package, listener access, app process state, kiosk restrictions, source notification visibility, source filters, and listener reconnect logs.

**Event detected but no repeat:** Inspect persisted pending count, next due, alarm permissions, AlarmManager schedule/re-arm, receiver invocation, process death, Doze, boot recovery, and ACK markers.

**Reminder posted but silent:** Check Android notification posting permission, app notifications enabled, channel importance/sound, DND, volumes, Samsung/OEM policies, and whether repeated notification IDs trigger actual alerting.

**App not running:** Inspect managed auto-run, bootstrap component, background restrictions, app disabled/force-stopped state, package signing/update, and watchdog status. Android force-stop cannot be bypassed reliably by an ordinary app.

**Knox rejects APK:** Compare signing certificate, package ID, versionCode, minSdk, APK integrity, deployment type, and policy requirements. Preserve a known-good release and rollback procedure.

**Logs missing/incomplete:** Inspect storage cap, daily rotation, ZIP errors, permissions, time boundaries, retention cleanup, and exporter status. Never treat an empty ZIP as proof there were no failures.

## Incident evidence
Collect device/build/policy metadata, local and UTC timestamps, test event sequence, source package, pending count, expected repeat times, actual repeat times, permissions/channel status, log ZIP, and whether the symptom reproduces outside kiosk. Escalate with reproducible steps and the precise code/firmware/policy combination.

## Rollout
Use a small pilot first. Do not broaden deployment until multiple locked-screen repeat cycles, restart/update, ACK, diagnostics, and recovery have been observed. Maintain rollback APK and documented Knox policy reversion.
