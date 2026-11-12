# 01 — Origin, Scope, History, and Decisions

## Original operational failure
MMT crews use managed Samsung phones to receive critical operational messages. The original concern was that a single audible notification may be missed while crews are working, the phone is locked, or Android background limits suppress activity. The requirement is not merely to display a notification: an unattended service must detect a supported incoming event, retain it until valid acknowledgment, and repeat a conspicuous alert at a configured interval, default 60 seconds.

The TraumaSoft MDT is an **Android application on the phone**, not a physical MDT terminal. The supported source families are Google Messages, Samsung Messages, AOSP MMS, TraumaSoft MDT and MDT v2, and missed-call notifications. Source-specific cancellation is not necessarily acknowledgment by a crew member.

## Operational principles
1. Crews should not configure or maintain the app. Knox administrators perform deployment and configuration.
2. The app must run without displaying an application window on kiosk startup.
3. IT must be able to access diagnostics through Knox Remote Support and export bounded logs without ADB in normal operations.
4. Persistent reminder state is more important than an attractive UI.
5. Do not claim guarantees beyond Android's actual permission, force-stop, notification channel, Doze, and background restrictions.
6. Logging must be detailed enough to investigate a missing reminder without collecting message bodies or other sensitive source content.
7. Resource usage must be bounded and measured, not assumed.
8. Tests must include process interruption, permission failures, duplicate events, partial acknowledgment, and recovery.

## Historical phases and rationale
**2026-11-05 — initial implementation.** The initial commit introduced repeating unacknowledged notifications. Soon after, missed-call monitoring, zero-touch Knox guidance, stable release artifacts, signing workflow, privacy-safe event logging, and IT log export were added. These changes addressed the transition from prototype to an application IT could deploy, update, and troubleshoot.

**2026-11-05 — persistence and lifecycle hardening.** Alarm recovery, listener reconnect, partial acknowledgments, deterministic fuzz models, atomic acknowledgment markers, and corruption handling followed. Their common rationale was to avoid a reminder disappearing silently because of a crash, stale callback, reboot, or corrupted local record. A deterministic model test demonstrates an invariant in the model; it does not establish that Android will audibly alert on a physical device.

**2026-11-05–06 — Knox and headless startup.** Invisible bootstrap activity and managed initialization reduced crew interaction. Package signing, APK metadata, and release verification were refined. This addressed reports that apps did not launch or were rejected as invalid by Knox. Signing identity and enterprise policy remain independent deployment checks.

**2026-11-11 — repeat continuity.** Changes preserved alarm deadlines when a burst of new notifications arrived and when only some pending items were acknowledged. Without this, repeatedly postponing a global alarm could indefinitely delay an already-pending alert.

**2026-11-12 — diagnostics and resilience.** Fourteen-day log retention, streaming ZIP export, isolated IT synthetic tests, periodic health checks, overdue recovery, channel diagnostics, and ZIP integrity improvements were introduced. These changes were motivated by the need to diagnose failures remotely and recover where Android permits, without changing the crew-facing workflow.

## Decision records to reconstruct from Git
For every material change, capture: date, exact commit SHA, defect or operational request, alternatives considered, rejected approaches, files changed, state transition before/after, tests added or run, physical-device evidence, risk, rollback plan, and subsequent regressions. If no evidence exists, mark it UNKNOWN rather than filling the gap with conjecture.

## Design tradeoffs
- **One global alarm vs one alarm per event:** One global reminder can bound scheduling overhead but requires rigorous deadline preservation, concurrency, and partial-ACK logic.
- **SharedPreferences JSON vs database:** Simple persistence reduces dependencies and memory footprint, but demands corruption recovery, atomic commits, bounded records, and careful synchronization.
- **AlarmManager vs periodic work:** Periodic jobs cannot meet a precise one-minute cadence; exact alarm privileges and Doze limitations must be surfaced honestly.
- **Notification listener vs reading message data:** The listener enables notification-based detection with minimal permissions, but may lose access, see package-specific semantics, or receive ambiguous removal reasons.
- **Automatic kiosk bootstrap vs visible app:** A hidden bootstrap supports zero-touch operation but makes independent diagnostics and status reporting essential.
- **Extensive logs vs privacy/storage:** Use event metadata, state transitions, rotation, retention, and ZIP export; avoid content and secrets.
