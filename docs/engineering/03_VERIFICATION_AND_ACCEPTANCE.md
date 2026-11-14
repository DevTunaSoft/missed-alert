# 03 — Verification, Test Evidence, and Acceptance

## Evidence policy
Every claimed PASS must include test name, exact command, commit SHA, environment, date, observed output, and applicable artifacts. Physical-device verification must identify model, Android version, One UI/Knox policy, app build/signature, and the observed alert behavior. Distinguish PASS, FAIL, BLOCKED, NOT RUN, and INCONCLUSIVE. Never infer fleet readiness from successful compilation.

## Automated test layers
**Pure JVM state-machine tests:** duplicate posts, generation changes, partial and full ACK, stale callbacks, burst scheduling, corrupt data, restart, repeat count, and randomized event sequences with reproducible seeds.

**Robolectric/Android integration tests:** AlarmManager scheduling/cancellation, PendingIntent identity, receivers, manifest component permissions, listener lifecycle, managed configuration parsing, notification channel state, ZIP export, and exception handling.

**Fault injection:** failed preference commit, corrupted primary/backup, permission revoked, notification listener disconnected, alarm canceled, process killed, disk full, clock changed, notification disabled, watchdog failure, and repeated boot events.

**Performance:** memory steady state/peak, CPU time per event, wakeups per hour, battery drain, file growth, export memory peak, and time to recover. Establish numerical budgets after measuring representative devices; do not invent measurements.

**Security/privacy:** exported components, intent spoofing, unsafe pending intents, accidental PII logging, ZIP traversal/path safety, diagnostics permissions, release signing, dependency scanning, and retention.

## Device acceptance matrix
Representative Samsung A14, A15, A16, A17 and XCover Pro, across supported Android/One UI versions, as available. Include kiosk enabled and disabled; screen on/off; Doze; low battery; reboot; app update; listener access toggled; notification permission toggled; DND; muted channel; exact alarm allowed/denied; source apps installed/not installed. Record unavailable combinations as BLOCKED or NOT RUN.

## Critical scenarios
T01 SMS received and first reminder emitted.
T02 TraumaSoft MDT notification received.
T03 TraumaSoft MDT v2 notification received.
T04 missed call detected without classifying a ringing call as missed.
T05 repeat occurs while unacknowledged over multiple cycles.
T06 explicit ACK stops subsequent repeats.
T07 source notification canceled by app does not automatically erase pending item.
T08 partial ACK preserves remaining alert's due time.
T09 burst of new events does not postpone oldest due reminder.
T10 duplicate/repost does not create runaway pending state.
T11 stale notification removal does not acknowledge new generation.
T12 process death retains pending state.
T13 reboot reconciles elapsed-time deadlines.
T14 exact-alarm denied uses documented fallback.
T15 listener reconnect resynchronizes active notifications.
T16 notification posting disabled is visible as degraded, not false PASS.
T17 channel muted or DND is diagnosed.
T18 watchdog recovers a recoverable missing schedule.
T19 force-stop limitation is documented and tested where safe.
T20 ZIP export succeeds and contains complete bounded records.
T21 ZIP failure leaves no misleading partial completed file.
T22 log rotation retains forensic history within configured caps.
T23 no message bodies or phone numbers in logs.
T24 14-day retention and total storage cap.
T25 synthetic IT tests do not alter the live pending queue.
T26 Knox auto-run requires no crew action and displays no kiosk UI.
T27 Knox Remote Support can export logs without ADB.
T28 APK signing and update compatibility verified.
T29 all monitored source packages tested against installed versions.
T30 long-duration locked-screen repeat and battery measurement.

## Acceptance gate
Do not label production ready until repeat reliability is demonstrated on actual representative managed phones, permissions and Knox policy are reproducible, signing/update path is verified, export is usable, privacy/security checks pass, performance is measured, and all P0 issues have evidence-backed dispositions. Publish exact unresolved risks and rollback steps.


## Google Messages acknowledgment pilot

At an existing reminder deadline, the app checks pending Google Messages generations against the SMS inbox. A single MessagingStyle message must match one inbox row by exact timestamp, sender and body. The app stores the timestamp and a fingerprint in the existing queue, and clears a generation only if the matched inbox row is marked read. Notification disappearance, unread or ambiguous rows, and unavailable SMS access leave the alert pending. Other sources follow their existing policy. Manual acknowledgment remains available.

The `sms_ack_evidence` event records the generation and decision without SMS content. `acknowledged_one` records whether other alerts remain. The pilot requires installer allowlisting and a runtime grant for Android's hard-restricted `READ_SMS` permission.

Device acceptance remains pending on the Samsung A15 with Android 16 and the production Knox profile. Verify permission provisioning, notification metadata matching the inbox for actual dispatch senders, ordinary conversation opening, unread swipes, same-thread replacement, multiple conversations, process restart, screen-off cadence and manual acknowledgment. Contact aliases, grouped or redacted notifications, and RCS can lack an exact SMS match and must retain reminders.
