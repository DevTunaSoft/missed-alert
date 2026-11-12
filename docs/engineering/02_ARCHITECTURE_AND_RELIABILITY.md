# 02 — Architecture, State Machine, and Reliability

## System boundary
Android receives source notifications from supported packages. NotificationListenerService classifies events and persists pending state. Scheduler arms a repeat alarm. The broadcast receiver reads current state and invokes the reminder notifier. Explicit acknowledgment changes durable state and cancels/reconciles scheduling. A coarse periodic watchdog diagnoses missing prerequisites and repairs recoverable state. Boot/update/time-change receivers restore appropriate scheduling. IT diagnostics export privacy-safe evidence.

## Current source map (reverify)
- `Config.kt`: monitored package list, managed configuration, 60-second default and unlimited repeat policy.
- `MissedAlertListener.kt`: posted/removed callbacks, active-notification resync, listener reconnect, source identity.
- `AlertStore.kt`: pending JSON, backup/corruption handling, durable ACK markers and generation-aware operations.
- `Scheduler.kt`: global AlarmManager deadline and fallback strategies.
- `ReAlertReceiver.kt`: reminder delivery attempt and self-rearm in finally.
- `Notifier.kt`: high-importance reminder channel, sound/vibration, alternate notification IDs, ACK actions.
- `RecoveryJobService.kt`: periodic health and alarm recovery.
- `EventLog.kt`: daily bounded logs and ZIP export.
- `BootstrapActivity`, `StatusActivity`, `ItDiagnosticsActivity`, `AckActivity`, `AckReceiver`, `BootReceiver`: deployment, IT, acknowledgment, and lifecycle boundaries.

## Alert state invariants
1. A newly detected eligible notification creates or updates one logical pending generation; unrelated notifications cannot acknowledge it.
2. A stale callback from an earlier generation cannot remove a newer generation.
3. A source app cancel is not necessarily user acknowledgment.
4. A valid explicit acknowledgment is persisted before the scheduler is reconciled.
5. A process crash cannot silently convert pending to acknowledged.
6. Partial acknowledgment leaves remaining items eligible for reminders without resetting their original due time.
7. A new alert burst cannot repeatedly postpone a reminder already due.
8. Duplicate events must not multiply pending reminders or unboundedly grow storage.
9. Reminder failure must leave the next recovery opportunity intact, where platform scheduling allows.
10. The watchdog must not claim a one-minute service-level guarantee; its cadence is coarser.

## Alarm and time semantics
Persist and log both wall-clock and monotonic timestamps where meaningful. `elapsedRealtime` is appropriate for measuring intervals within one boot but resets at reboot. Reconcile deadlines after reboot and clock/timezone changes. Examine how `setAlarmClock` affects system UI, whether exact alarm access exists on each Android version, and what `setAndAllowWhileIdle` does under Doze. Measure actual repeat gaps, not merely scheduled timestamps.

## Concurrency and recovery
Stress concurrent notification-post, notification-remove, ACK, boot receiver, watchdog, and alarm receiver events. Test durable commit failures and partially malformed JSON. Validate that synchronized access covers every read-modify-write transaction, and that callback generations prevent stale ACK. Verify PendingIntent identity and cancel semantics. Preserve the correct next deadline after partial ACK, process restart, and source re-post.

## Reminder audibility
Posting a high-importance notification is not sufficient proof of an audible reminder. Android 13+ posting permission, channel importance/sound, DND, battery restrictions, system volume, notification access, OEM policy, and screen/Doze state all affect outcomes. Test actual sound/vibration on representative Samsung devices. Document which restrictions can be managed centrally by Knox and which require provisioning or user/admin intervention.

## Diagnostic event schema
For each event, record ISO wall time, monotonic uptime, event type, source package, anonymized/stable event identifier, pending count, repeat interval, next due, listener/permission/channel/power state, outcome, and error classification. Do not log notification text, sender, phone number, message body, tokens, or credentials. Use bounded queue/files and streaming export to avoid heap spikes.

## Known risks to audit
- Current daily logging may truncate earlier same-day history when the 3 MiB file limit is reached; use segment rotation and test retention completeness.
- A backup containing the same generation as the primary does not protect against every corruption mode.
- Permission denial and force-stop may prevent recovery; neither should be disguised as a code-level PASS.
- Some README descriptions may conflict with actual minimum SDK or acknowledgment behavior.
- Notification channel configuration can be immutable after first creation; channel recovery needs evidence.
