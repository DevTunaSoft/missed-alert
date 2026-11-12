# THE MMT MISSED ALERT ENGINEERING HISTORY BOOK

**Edition III — Historical narrative, decision rationale, forensic notes, and Codex continuity manual**  
**Repository:** `DevTunaSoft/missed-alert`  
**Prepared:** 2026-11-12  

> This book combines reconstructed project intent with observed repository evidence. It does not pretend that conversations are equivalent to deployed functionality or that unrun tests passed. Codex must verify and extend every historical claim using commit diffs, CI, device logs, and the actual current branch.

## Reading map

**Part I — The story and the reasons** explains why this application exists, how requirements changed, what was implemented, what tradeoffs were chosen, what went wrong, and how future changes must be justified. **Part II — The master engineering dossier** preserves the original 92-folio technical specification, acceptance catalogs, deployment procedures, and takeover directives. Together they are the continuity source for new engineers and Codex sessions.

---

## Book Chapter 01 — The operational story: why the software was commissioned

MMT's frontline workflow depends on a crew noticing an incoming communication, not merely on an Android notification being posted. The central failure scenario is an alert that appears or sounds once and then disappears into the background of a busy shift. The design response was not to replace the source applications, but to add an independent reminder layer that continues until the relevant event is acknowledged. This distinction must remain explicit: a successfully posted Android notification is not proof that a human saw it, understood it, or acted on it. The application can establish delivery and acknowledgment signals only to the extent the Android platform and source application expose them.

The initial target included Google Messages and TraumaSoft MDT notifications; missed-call detection was subsequently represented in the repository. The term MDT refers to a separate Android application installed on the phone, not to a dedicated MDT hardware device. That clarification is operationally significant because the solution must observe another application's notification rather than integrate with a vehicle terminal. The crew should not be required to open, configure, or supervise the reminder app. The application must remain outside the visible kiosk launcher while still receiving the privileges needed to function. This zero-touch requirement drives enrollment, bootstrap, permissions, recovery, diagnostics, and all subsequent architecture decisions.

The governing success criterion is therefore an observable chain: source app posts an eligible event; listener captures it; pending state is persisted; a reminder is scheduled; the system emits successive reminders at the configured cadence; an explicit, well-defined acknowledgment stops only the appropriate reminder(s); the evidence survives long enough for IT to reconstruct what happened. Any broken link is a service failure. The program must be assessed end to end, not by whether a single unit test or APK build passes.

## Book Chapter 02 — The chronology of design decisions, without invented history

This chapter reconstructs *decision sequence*, not an exact dated timeline. Only commit timestamps and content in GitHub can establish the date of a specific implementation. Conversation requirements can establish intent and user preferences, but cannot prove that a feature shipped. Codex must enrich this chapter from `git log --all --date=iso --stat`, commit diffs, pull requests, issues, CI, and release artifacts. Never invent meeting dates, authors, device observations, or motives.

**Phase A — one-shot alerts were insufficient.** The motivating report was that the alert sounded once but did not repeat. This made recurring scheduling and acknowledgment tracking first-order concerns. A simple UI reminder was rejected as inadequate because crews cannot be expected to keep the app open. A background `NotificationListenerService` became the logical event intake.

**Phase B — background execution and Knox became central.** A kiosk-managed phone may behave differently from a consumer Android device. An app that compiles or works outside kiosk is not necessarily viable under Knox policy. The design grew to include headless bootstrap, managed configuration, notification access, posting permission, alarm access, battery exemption, and kiosk allowlisting. None of these settings should be assumed effective merely because an MDM policy was assigned.

**Phase C — scheduling reliability and recovery.** Android Doze and background restrictions made a naïve timer or periodic job inappropriate for a promised one-minute cadence. The repository uses a single self-rearming alarm-clock alarm where exact-alarm privileges permit it, with an inexact fallback and a coarse watchdog. This was a reliability tradeoff: a visible system alarm indicator and policy requirements in exchange for a stronger timing mechanism. The watchdog is a safety net, not an alternate 60-second clock.

**Phase D — acknowledgment semantics grew more precise.** Source-app removal is not necessarily evidence of a human acknowledgment. The current listener retains an alert on some source-app/system removal reasons and acknowledges user-dismissal reasons. This differs from older README language claiming any removal ends the reminder. Codex must resolve the product contract, update documentation, and test actual TraumaSoft and Messages behavior before altering semantics.

**Phase E — diagnostics became a field-support requirement.** The user requested that IT connect with Knox Remote Support, view the device, and pull logs without ADB or crew involvement. Daily logs, bounded retention, export to ZIP, status reports, and self-test telemetry were added or proposed to make field failures diagnosable. The latest observed commits on 2026-11-12 focus on ZIP status formatting, atomic-looking export behavior, retention, and watchdog isolation. These commit titles are evidence of intended work, not independent proof of successful execution.

**Phase F — platform breadth and release readiness.** The baseline was expanded to include XCover Pro and A14–A17. The current Gradle configuration uses `minSdk=29`, `compileSdk=36`, `targetSdk=36`, version `1.2.4-pilot`. This conflicts with older README claims of Android 13+ and minSdk 33; the build file is stronger evidence for the current code. Physical acceptance remains mandatory.

## Book Chapter 03 — The original promises and the non-negotiable boundaries

The promise was *autonomous operation*, not unlimited control over Android. The app must remain lightweight, quiet when healthy, and visible to IT when something is wrong. Crew intervention must not be required to start the service, grant a runtime permission, export logs, or restart a failed background component. Enrollment and MDM are expected to establish prerequisites. Where Android or Knox cannot silently grant a privilege, the system must report a blocker rather than fabricate success.

No background component may claim that a notification was heard. System sound routing can be affected by mute, DND, Bluetooth, notification-channel settings, OEM policies, notification cooldown, and hardware speaker behavior. The software can record configured and observable states; a real-device acoustic test is needed to establish audible behavior. Likewise, a notification's disappearance must not be equated with a crew reading a message without an agreed and tested acknowledgment rule.

The design excludes a cloud dependency, unnecessary analytics SDKs, third-party tracking, broad SMS database access, and persistent busy polling. It also excludes arbitrary self-granted privileged access, defeating Android restrictions, or a requirement to keep an activity on screen. Preserve the separation between the reminder app and Volume Guard: Volume Guard enforces audio levels where permitted; Missed Alert tracks and repeats pending communications. One app does not replace the other.

## Book Chapter 04 — A guided tour of the current code and the reasons behind its shape

The app is a Kotlin Android application with framework APIs and Robolectric/JUnit test dependencies. `MissedAlertListener` is the intake boundary. It observes notification posts/removals, filters watched packages, excludes ongoing/group-summary notifications, handles missed-call heuristics, and attempts resynchronization on listener reconnection. This structure avoids a foreground polling loop and delegates notification observation to Android.

`AlertStore` persists pending items, acknowledgment markers, repeat counters, and scheduling metadata in SharedPreferences. Its backups and `commit()` calls indicate an effort to preserve state through crashes. The engineering reason is simple: an app process can die between the first alert and the next reminder, so pending state must not be merely in memory. However, `nextAt` is expressed in `elapsedRealtime`, which resets after reboot; Codex must investigate and test the reboot path, not presume correctness. The backup and primary values are currently written in the same preferences transaction, which is not equivalent to an independent historical snapshot; test corruption and power-loss assumptions carefully.

`Scheduler` owns a single alarm. It selects `setAlarmClock` when exact-alarm scheduling is available and `setAndAllowWhileIdle` otherwise. The choice is designed to avoid a busy service, but the fallback does not guarantee the configured cadence. `ReAlertReceiver` posts reminders and re-arms the next alarm in a `finally` path so notification failures do not silently break the chain. `RecoveryJobService` is a periodic watchdog and passive self-test. It checks listener access, notification posting, channel state, battery exemption, and alarm health, and attempts permitted recovery. It is not a proof of end-to-end alert audibility.

`Notifier` uses a high-importance channel, system default notification sound, vibration, and alternating notification IDs to encourage a fresh alert on each repeat. The two-ID strategy should be validated across Android versions, OEM channel behavior, and notification cooldown. `EventLog` writes timestamped daily files, caps daily size, retains a nominal 14 days subject to storage limits, and streams ZIP exports. The reason for streaming is bounded memory; the reason for daily files is reconstructable chronology. Its current daily-size rotation rewrites the day's file when the limit is reached, potentially destroying earlier same-day evidence. That risk deserves explicit tests and an archival rotation design.

`Config` reads managed restrictions and supplies watched package defaults. The repository includes `com.traumasoft.mdt`, `com.traumasoft.mdtv2`, Google Messages, Samsung Messages, and AOSP MMS, plus managed extras. The package allowlist is a hypothesis about deployed application identities; the actual installed package and channel must be captured from a pilot device. `NotificationRemovalPolicy` separates user dismissals from app/system removals. That policy must be tested against real source-app behavior and against notification key reuse.

## Book Chapter 05 — A detailed case study: the alert sounds once and never repeats

**Symptom.** A first alert is perceived, but the next one never arrives. This symptom does not identify one root cause. It could reflect failure to capture the source notification, lost pending state, a missing alarm, a fired alarm that fails to reschedule, disabled notification posting, a silent channel, a muted route, a process that is force-stopped, or an acknowledgment mistakenly inferred from notification removal.

**Investigation sequence.** First, collect a device-status export and daily logs before restarting or reinstalling. Correlate source event time, listener connection, `alert_tracked`, `alarm_scheduled`, `alarm_fired`, `reminder_posted`, and `alarm_scheduled` again. If `alert_tracked` is missing, inspect notification access and the actual source package/channel. If tracking exists but scheduling does not, inspect store state, exceptions, and concurrency. If scheduling exists without firing, inspect exact-alarm privileges, Doze, force-stop, reboot, elapsed-time deadlines, and kiosk policy. If firing and posting both exist, inspect channel importance, notifications enabled, DND, audio route, cooldown, and acoustic evidence. If a pending item disappears, trace acknowledgment and notification-removal reason.

**Why this diagnostic sequence matters.** Blindly changing the alarm interval or repeatedly launching the app can hide the original defect, corrupt the timeline, and produce a misleading green build. Every fix must name its causal hypothesis, reproduce the failure, show the changed code path, and include a regression that would have failed before the fix. A physical-device result should include timestamps, firmware, policy profile, package version, audio observations, and export integrity. A simulated test is useful but is not an acoustic acceptance test.

## Book Chapter 06 — A detailed case study: Knox kiosk enrollment and zero-touch bootstrap

**Operational constraint.** The crew must not see or open the reminder app in the kiosk, but the Android system must still bind its notification listener and deliver scheduled alarms. The app therefore includes a bootstrap launcher component and an IT-only status surface. The deployment sequence must distinguish installing, launching once, granting privileges, allowing background operation, and hiding the UI. These are separate state transitions.

**Policy layers.** Knox Manage distributes and launches the APK. Knox Service Plugin or supported management controls may configure special permissions and battery restrictions, depending on firmware, enrollment mode, plugin version, and license. The kiosk allowlist determines whether the package can run without being offered as a crew launcher. Notification policy determines whether source and reminder notifications are posted and audible. The package must not be force-stopped by routine management operations. An assigned policy is not enough: the IT diagnostics must show effective listener access, posting permission, channel importance, alarm access, and power exemption.

**Failure lessons.** Earlier project discussions included 'invalid app' uploads and app-start failures. Treat these as reported symptoms, not proven root causes. Signing, versionCode progression, keystore continuity, package identity, installation restrictions, and kiosk visibility are separate possibilities. Codex should not regenerate a signing identity or change the package ID to work around a deployment failure without checking update compatibility. A kiosk-hidden app that has never been started may not receive expected events. A successful remote launch is not proof that it will survive reboot, Doze, or a policy refresh.

**Acceptance.** A clean enrolled phone should receive the signed release, complete bootstrap without a persistent visible activity, acquire effective permissions, receive an actual source alert, repeat at the expected cadence, survive screen-off and Doze, recover after reboot or update as designed, and export a diagnostic ZIP through the IT-approved Knox Remote Support procedure. Document the exact clicks, policy keys, firmware, and observed outcomes for every device family.

## Book Chapter 07 — A detailed case study: logging evolved from troubleshooting aid to evidence system

The user's field-support model is deliberately simple: IT remotely views the phone and retrieves logs, as with the companion Volume Guard workflow. The application should log autonomously; crews should not create incident reports to make diagnostics work. That requirement changes what counts as adequate telemetry. A single 'alarm scheduled' line is not enough to reconstruct an alert cycle. The log must expose event intake, identity classification, pending count, scheduling intent, actual alarm delivery, reminder posting, acknowledgment reason, recovery action, and the permissions/power context at failure time.

The current `EventLog` design writes per-day files with wall time and uptime and exports a ZIP without loading every file into RAM. It has a 14-day retention constant, a 3 MiB daily cap, and a 50 MiB aggregate cap. These are current source facts, not promises that 14 complete days always survive heavy traffic. In particular, the daily-size limit currently rewrites the active file with a rotation marker, losing preceding lines. A robust redesign should use bounded numbered segments per day, never overwrite the only copy of recent evidence, expose drop/rotation counters, and make export safe during concurrent writes. Test midnight, DST, timezone changes, clock rollback, low disk, ZIP cancellation, partial downloads, and corrupted entries.

Log privacy is a core design decision. The listener sees message title/text in memory, but the diagnostic event stream must not record message bodies, contact names, telephone numbers, authentication tokens, or other unnecessary content. Record package, channel, stable redacted event identifier, timestamps, reason codes, state transitions, and error classes. The ZIP must contain a manifest identifying application version, device/OS context, file inventory, checksums, capture time, retention gaps, and explicit caveats. Logs are evidence, not a guarantee that every audible event occurred.

## Book Chapter 08 — The change ledger: how to record why, not just what

Every material code change must have a durable change-decision record. Use this schema: **change ID; date/time; author/agent; baseline SHA; observed symptom; reproduction steps; expected behavior; actual behavior; evidence references; root-cause hypothesis; competing hypotheses; options considered; selected option; why selected; rejected options and why; implementation paths; security/privacy impact; memory/battery impact; compatibility impact; tests added; tests executed with commands and outputs; physical-device evidence; known limitations; rollback procedure; final commit SHA; follow-up owner/status**.

A commit message such as 'fix alert repeats' is not enough. A meaningful record would explain why a previous scheduling path failed, how a new path avoids the same race, and which test proves the old bug cannot recur. When an investigation finds no code defect, record that too: a Knox policy or notification-channel configuration may be the actual cause. Never force a code change merely to have a deliverable.

The ledger must separate *reported issue*, *hypothesis*, *verified root cause*, *implemented change*, *automated test passed*, *device test passed*, and *production observation*. A failed or skipped test remains visible; it cannot be relabeled as passing because a later unrelated commit succeeded. Reopened issues must link to the earlier decision record. This protects the project from cycling through the same failed solutions.

## Book Chapter 09 — Commit history: what is actually established as of this handoff

The repository `DevTunaSoft/missed-alert` was accessed on 2026-11-12. The observed default-branch README is at blob SHA `73aea42842e3fd125b434af30cff0a49ad6ea635`. Recent observed commit SHAs and titles are listed below. These establish that changes were committed under these descriptions; the full diffs, tests, and deployment evidence must still be inspected.

- `730530b4e8cedd2b05782574d30efb39ee42d7c8` — Add regression tests for safe reminder channel recovery.
- `64afae3ff8c9d6cc25eaeff2664ac98a1badef59` — Isolate alarm recovery from watchdog diagnostic failures.
- `8256410bbd4f0b94cd7602b8e5a4560617ad0403` — Test diagnostic retention and ZIP export integrity.
- `f28f2245418a040b1eaec12cfdd680bd997500b2` — Isolate retention test from cached singleton prune date.
- `5adaf01704b00adcc33c0b1673b7a407aff4be44` — Test bounded daily diagnostic rotation and continued event logging.
- `0c12777a92839bc3b1dc15e0a94edf37cd8dcef7` — Prevent partial diagnostic ZIP files from appearing in Downloads.
- `509b7e0d72a33b89c9c9b64a01030531566b76b1` — Preserve newline formatting in exported device status.
- `fe19287723293c017ed9fa595863908941edf47d` — Correct ZIP device status line breaks.

Two branches were observed: `main` and `pilot/xcover-pro-reminder-recovery`. The user prefers one authoritative mainline, not multiple divergent deployed app versions. Codex must examine the pilot branch, compare it with main, and identify unique unmerged changes before deleting, merging, or ignoring anything. No commit is evidence of a successful Samsung acoustic test without a linked test record. The repository README itself contains a historical 'Not yet compiled' warning inconsistent with more recent work; Codex must reconcile documentation against CI and artifacts rather than accepting either statement uncritically.

## Book Chapter 10 — Engineering alternatives and tradeoff explanations

**Polling versus notification listener.** Polling risks battery use, latency, and unavailable cross-app data. A notification listener offers event-driven delivery but requires special access and can be disconnected. The current architecture chooses event-driven intake plus a recovery check. Codex should preserve that principle unless measurements prove it inadequate.

**Handler timer versus AlarmManager.** In-process timers are fragile across process death, Doze, and screen-off. AlarmManager is system-mediated but subject to permissions and scheduling restrictions. The current exact alarm-clock path is designed to preserve cadence, with an explicit inexact fallback. A 15-minute periodic JobScheduler task is not a substitute for a one-minute reminder. The visible alarm icon is a known tradeoff and must be documented for IT.

**One notification ID versus alternating IDs.** Reposting the same notification may not produce a new audible alert under every Android/OEM behavior. Alternating IDs attempts to improve repeat signaling, but cancellation/posting ordering and notification cooldown still need physical verification. The decision should be justified with measured behavior, not superstition.

**Acknowledge on source removal versus retain until explicit action.** Automatic removal-based acknowledgment is convenient but can falsely silence alerts when the source app clears or replaces a notification. Retaining alerts reduces false acknowledgments but can produce stale reminders after a legitimate workflow. This is a product safety decision requiring actual source-app testing and explicit policy, not just a coding preference.

**SharedPreferences versus a database.** Preferences minimize dependencies and memory overhead for a small queue, but JSON parsing, repeated full-array scans, key reuse, and crash consistency may become problematic. A database introduces migrations and complexity. Measure alert volumes and failure modes before changing storage. Do not optimize for imaginary scale at the expense of simple reliability.

**Verbose diagnostics versus privacy/storage.** High-volume logs aid field investigation but can consume storage, increase I/O, and leak sensitive content if badly designed. Use structured metadata, bounded segments, retention counters, and redaction. Preserve enough evidence to reconstruct a failure without collecting message content.

## Book Chapter 11 — What has not been proven and must not be claimed

A buildable APK is not proof that the service runs in Knox kiosk. Robolectric tests are not proof that a Samsung A16 plays a sound under Doze. A successful `NotificationManager.notify` call is not proof that a crew heard the alert. A watchdog scheduled with JobScheduler is not proof it will execute exactly at 15 minutes. A permission configured in Knox is not proof the permission is effective on the device. A ZIP export function in source is not proof the operator can retrieve a completed ZIP through Knox Remote Support.

At this handoff, physical-device acceptance for all target hardware/firmware/policy combinations remains unverified in the evidence examined. The actual source notification package/channel for TraumaSoft in the deployed fleet has not been demonstrated in this document. Production telemetry and crew acknowledgment behavior have not been independently verified. CI workflow names and recent job results were not comprehensively audited. The dossier therefore must not declare production readiness. Codex's first action is to close these evidence gaps or mark them BLOCKED with exact required inputs.

## Book Chapter 12 — A model incident postmortem and a model successful change record

**Incident template (illustrative; not a historical event):** At 14:03 a source notification arrives; `alert_tracked` records pending=1. At 14:03:01 `alarm_scheduled` records alarm-clock mode with 60,000 ms. At 14:04:01 `alarm_fired` occurs; `reminder_posted` records notification ID. No subsequent `alarm_scheduled` appears. Hypothesis: an exception interrupted the rearm path. Evidence required: exception type, code branch, version, complete logs. Corrective action: reproduce under a failing test that throws from notification posting; verify `finally` rearms the alarm; collect new device logs. This is an example of how to document a failure, not a claim that this sequence happened.

**Change record template (illustrative):** `DEC-YYYY-NNN`; title 'Preserve reminder schedule after notification failure'; trigger 'one-shot reminder report'; baseline SHA 'to be captured'; alternatives 'in-process retry, JobScheduler, AlarmManager self-rearm'; chosen 'self-rearm in finally'; rationale 'system scheduling with bounded overhead'; tradeoffs 'permission requirements and potential icon'; acceptance 'forced Notifier exception still schedules next alarm; physical idle test'; status 'unverified until CI and device logs attached'. The historical repository currently has a `finally` path; Codex should trace when it was introduced before turning this illustrative record into a verified historical entry.

Every incident chapter should include a timeline, system state, expected behavior, actual behavior, root cause with confidence level, five-whys analysis where supported, contributing policy/device factors, corrective change, independent reviewer, regression evidence, field validation, rollback, and follow-up observations.

## Book Chapter 13 — Codex takeover: the first 72 engineering hours

This is an execution order, not a claim that Codex can run continuously for 72 hours. Codex must checkpoint and resume within actual session and platform limits.

**Block 1 — repository truth.** Identify default branch and current SHA; inventory files, commits, open PRs, issues, CI workflows, test reports, releases, signing approach, and deployed version. Compare `pilot/xcover-pro-reminder-recovery` to main. Generate a discrepancy report between README, source, and this dossier. Do not edit code until the baseline is recorded.

**Block 2 — causal analysis.** Draw the complete source-post-to-ack state machine, map every callback and exception path, and construct tests for first-repeat, fifth-repeat, multiple simultaneous alerts, notification update, user dismissal, source cancellation, process death, reboot, clock change, and exact-alarm denial. Record which tests fail before changes.

**Block 3 — narrow fixes.** Address verified highest-severity defects one at a time. For each fix, capture baseline failure, patch, test evidence, impact on resource use, and rollback SHA. Avoid large rewrites. Keep the main branch authoritative; use temporary isolated workspaces only if necessary for safe verification.

**Block 4 — deployment and observability.** Validate signed release continuity, APK manifest, managed restrictions, permissions, headless bootstrap, kiosk visibility, and export retrieval. Add self-test coverage without generating false alerts or modifying acknowledgment state. Measure memory, CPU, wakeups, battery, and log I/O.

**Block 5 — device campaign.** Run a documented test matrix on the actual Samsung models and Android versions, including screen-off, Doze, 24-hour idle, reboot, update, and multiple pending events. Capture real sound evidence and export ZIPs. If devices are inaccessible, mark physical tests NOT RUN and produce a precise IT operator checklist rather than claiming PASS.

**Block 6 — release gate.** Require passing reproducible CI, documented security/privacy review, a signed update compatible with existing installations, effective Knox policies, device acceptance, rollback instructions, and no unresolved critical failures. Keep the dossier, README, user manual, roadmap, feature status, and engineering ledger synchronized.

## Book Chapter 14 — A permanent continuity system that prevents institutional amnesia

Maintain `docs/MASTER_ENGINEERING_DOSSIER.md` as the long-form narrative; `docs/ENGINEERING_DECISIONS.md` as immutable decision records; `docs/CHANGE_HISTORY.md` as the chronology of implementation and rationale; `docs/INCIDENTS.md` as root-cause postmortems; `docs/VERIFICATION_LEDGER.md` as commands/results/evidence; `docs/DEVICE_MATRIX.md` as hardware/firmware/Knox acceptance; `docs/ROADMAP.md` as current prioritized work; `docs/FEATURE_STATUS.md` as truth table; `docs/USER_MANUAL.md` as operational instructions; and `docs/RESUME.md` as the exact next-session entry point. Reuse existing files if they already serve these purposes. Avoid duplicate contradictory records by assigning each type of fact one authoritative home and linking rather than copying mutable status.

A new engineer must be able to answer, without access to the original conversation: What was the original operational risk? Why was an event listener chosen? Why does the alarm-clock path exist? Why is the app hidden in kiosk? Why are source removals treated cautiously? What broke during previous deployments? What did each recent commit intend to fix? Which tests really passed? What remains unproven? Which package and signing key are deployed? What is the safe rollback? Which exact command and task should be run next?

Every work session ends with a concise checkpoint: starting SHA, ending SHA, changes, causal rationale, test commands and outcomes, artifacts, known regressions, device evidence, blockers, next priority, and the exact resume command. If no changes were made, record that truth. This documentation is not paperwork after development; it is part of the system's reliability engineering.

## Book Chapter 15 — Source-of-truth annotations and confidence levels

Use **VERIFIED-SOURCE** for statements directly visible in inspected code or configuration; **VERIFIED-COMMIT** for a commit SHA and title/diff actually retrieved; **VERIFIED-CI** for executed jobs with links and timestamps; **VERIFIED-DEVICE** for a physical device report with model, OS, policy, and evidence; **REPORTED** for user-observed behavior; **INFERRED** for plausible reasoning from code; **PROPOSED** for an unimplemented recommendation; and **UNKNOWN** for evidence gaps. Never promote a statement from one class to another without new evidence.

For example, 'EventLog has RETAIN_DAYS=14' is VERIFIED-SOURCE; '14 complete days are always available' is NOT established, because size limits and rotation can remove records. 'ReAlertReceiver has a finally-based reschedule' is VERIFIED-SOURCE; 'alerts reliably repeat under all Samsung Doze modes' is UNKNOWN pending device validation. 'The app once alerted but did not repeat' is REPORTED; its specific root cause is UNKNOWN until correlated logs and reproduction exist. This vocabulary should appear in change records, release reports, and Codex summaries.

## Book Chapter 16 — The questions a future engineer must be able to answer

Before modifying alert intake: Which exact app package emitted the source event? Is it a new notification or an update? What does `postTime` mean for that source? Can a key be reused? Which removal reasons reflect a crew action? Does the source clear its notification automatically? Can a notification be grouped or ongoing? What happens on listener reconnection?

Before modifying scheduling: What clock domain stores the deadline? What happens after reboot? What happens if exact alarms are revoked? What if an alarm fires twice or is delivered late? What if two alerts arrive simultaneously? What if notification posting throws? What if the watchdog runs concurrently? What happens when the maximum repeat count is reached? How is a failure surfaced to IT?

Before modifying logging: Can any message body, contact, number, or credential enter the export? Are writes atomic enough for a power loss? Can a large day overwrite earlier evidence? Does ZIP creation stream? Is a partially written export visible as complete? Can IT retrieve it in kiosk through Knox Remote Support? Can a log failure interrupt the reminder path?

Before releasing: Can the APK update the installed package with the same signing identity? Is the version code monotonic? Does the kiosk start the app invisibly? Are permissions effectively granted? Is the source notification allowed? Is the reminder channel audible? Are physical tests actually recorded? Is rollback possible without data loss? If any answer is unknown, document the exact experiment needed.


---

# PART II — ORIGINAL MASTER ENGINEERING DOSSIER (PRESERVED IN FULL)

# MMT Missed Alert — Master Engineering Dossier & Codex Takeover Package

**Project:** DevTunaSoft/missed-alert  
**Prepared:** 2026-11-12  
**Purpose:** Comprehensive, standalone engineering specification, forensic baseline, roadmap, test protocol, operations runbook and Codex continuation contract  
**Status:** Requirements and inspected snapshot only; no clean build, CI run or physical Knox/audio acceptance was executed while preparing this package.  
**Safety:** This project supports alert visibility in an EMS environment; do not imply guaranteed clinical alert delivery without observed field testing.

## How Codex must use this dossier
1. Read the full dossier and current repository before changing code.
2. Treat **observed snapshot** as dated evidence; code and current GitHub status always supersede stale assumptions.
3. Preserve existing implementation; diagnose and regress rather than rewrite by default.
4. Keep a living ledger and update the dossier each verified cycle.
5. Follow the exact execution protocol and definition of done at the end.

**Document navigation:** Chapters are numbered as engineering folios. Each folio is an independently actionable section; page counts differ by viewer, font and print settings. The file intentionally contains substantial detail rather than artificial blank pages.

---

## Folio 01 — Mission, mandate, and clinical-operational context

This document is the controlling engineering handoff for DevTunaSoft/missed-alert, the MMT Missed Alert application. The deployment context is a managed Samsung phone used by ambulance crews. The phone is not an MDT: TraumaSoft MDT is a separate application running on the phone, alongside Google Messages and possibly Samsung/AOSP messaging. Missed-call reminders are also in documented project scope. The principal failure to prevent is an important crew notification arriving once and then being overlooked because repeated audible reminders fail. This is safety-relevant software; neither unit tests nor a green CI badge prove a crew heard a notification.

**Desired outcome:** With device policy correctly installed, source notifications enabled, and legally/platform-permitted access granted, the app discovers eligible notifications, creates an independently tracked pending item, schedules a repeat, produces subsequent audible/vibrating reminders at the configured interval until explicit acknowledgment, and recovers after ordinary Android lifecycle interruption. It is managed by IT, not configured by crews. IT must be able to inspect health and export evidence while remotely supporting the phone.

**Non-goals:** Do not turn the phone into an MDT, duplicate TraumaSoft dispatch workflows, read SMS databases, bypass Android permission security, or guarantee Doze timing that cannot be proved on actual firmware. Do not create invasive tracking of crew message contents. Do not refactor successful systems merely for style; reliability and minimal resource consumption take precedence.

The handoff is a mission specification and investigation guide, not a statement that acceptance testing has already passed. Codex must read repository state first, then reconcile this document with the latest code. Every claim in future reports must be tied to commits, test runs, field evidence, or a clearly labeled inference.

---

## Folio 02 — Authority, governance, and evidence hierarchy

Codex is authorized to inspect, modify, test, build, harden, and document within the established project scope without repeatedly requesting permission for routine engineering choices. Security-critical scope expansion, deployment to active clinical devices, procurement, disclosure of secrets, destructive repository changes, and changes requiring device administrator approval remain gated.

Use this evidence order: (1) current checked-out code and deterministic tests; (2) GitHub commit SHA and CI logs; (3) on-device diagnostic exports from a specified phone/OS/Knox policy; (4) current authoritative vendor/platform references; (5) historical project conversations or existing prose; (6) assumptions. A repository README is valuable as a requirements artifact, but it can be stale. A commit message indicates intent, not passing tests. Device screenshots cannot substitute for listener callback traces.

Maintain a single append-only engineering ledger containing timestamp, branch, start SHA, end SHA, reason, files changed, tests attempted, results, evidence artifact, blockers and next step. Mark every line IMPLEMENTED, VERIFIED BY TEST, VERIFIED ON DEVICE, NOT VERIFIED, BLOCKED, or REJECTED. Never use 'fixed' when only a code change or theory exists.

Work on `main` as the integration target, respecting existing repository protection. If temporary isolated branches are necessary for safety, merge verified work to `main` and retire redundant lines. Do not force-push protected history or commit keystores. Preserve the ability to revert a bad release and identify which APK, signing certificate, commit, and policy revision was deployed.

---

## Folio 03 — Snapshot provenance and exact known facts

**Inspection date:** 2026-11-12. **Repository:** https://github.com/DevTunaSoft/missed-alert. **Observed branches:** `main` and `pilot/xcover-pro-reminder-recovery`. **Most recent observed commit:** `fe19287723293c017ed9fa595863908941edf47d` (2026-11-12 18:48:26 UTC), described as 'Correct ZIP device status line breaks'. The inspected README blob SHA was `73aea42842e3fd125b434af30cff0a49ad6ea635`. These are snapshot anchors, not immutable future state. Re-run branch/head/CI discovery at takeover.

Inspected files: `README.md`, `docs/XCOVER_KNOX_PILOT.md`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, and `app/src/main/java/com/crewalert/missedalert/MissedAlertListener.kt`. No full repository tree, complete tests, issue list, PR list, CI run history, signing configuration, deployed firmware logs, or runtime test outputs were available in this inspection. Therefore this dossier MUST NOT assert that the build passes or that reminders are reliably audible.

**Build metadata observed:** namespace/applicationId `com.crewalert.missedalert`, `minSdk=29`, `compileSdk=36`, `targetSdk=36`, `versionCode=7`, `versionName=1.2.4-pilot`, Java/Kotlin JVM 17. Root uses Android Gradle Plugin 8.13.2 and Kotlin plugin 2.3.10. Test dependencies shown: JUnit 4.13.2 and Robolectric 4.16.1. No third-party runtime dependencies were declared in the inspected module Gradle file. The README describes an older Android 13+ minimum and says 'not yet compiled', but the current Gradle file targets Android 10+; resolve these inconsistencies rather than repeating obsolete claims.

**Recent observed commit subjects:** ZIP device-status line breaks; exported device-status newlines; prevention of partial diagnostic ZIP files; bounded daily log rotation; diagnostic retention/ZIP integrity tests; watchdog recovery isolation; reminder-channel recovery regression tests. Confirm their actual diffs and execution evidence.

---

## Folio 04 — Original scope and zero-touch constraints

The crew should never have to launch the app, enter configuration, grant permissions, reopen it after restart, or maintain its monitoring state. IT owns deployment through Knox Manage and Knox Service Plugin. This zero-touch requirement cannot override Android's app lifecycle or permission models. 'Assigned' and 'effective' permissions are distinct, and pilot diagnostics must show effective state.

The app should not open an intrusive screen at kiosk startup. The inspected manifest has a launcher-capable `BootstrapActivity` with `Theme.NoDisplay`, `noHistory`, and `excludeFromRecents`, designed for managed initial activation. `StatusActivity` is not exported; `ItDiagnosticsActivity` is exported with the signature `android.permission.DUMP`; `AckActivity` is not exported. Check the resulting launch, permission, and background behavior in actual kiosk conditions. Document exactly how IT enters diagnostics without making a crew-facing icon.

Control surface: managed app configuration where available (`extra_packages`, `interval_seconds`, `max_repeats` from README), remotely supplied Knox rules, IT status, controlled export, and a self-test harness. Crew-facing controls must be limited to safe notification acknowledgment routes. No additional service requiring staff credentials, servers, cloud accounts, or paid APIs should be introduced without explicit project need.

The original project also calls for clear daily logs retained at least seven days, the ability to inspect them over Knox Remote Support, and autonomous testing. Make the maintenance model understandable to an IT generalist and robust enough for engineers to diagnose failures without guessing.

---

## Folio 05 — Functional scope and source classification

Supported sources include Google Messages (`com.google.android.apps.messaging`), Samsung Messages (`com.samsung.android.messaging`), AOSP MMS (`com.android.mms`), TraumaSoft MDT variants (`com.traumasoft.mdt` and `com.traumasoft.mdtv2`), additional configured package names, and selected missed-call notification providers. Confirm the actual packages present on pilot phones; application brand and package may differ across device updates and regional builds.

A notification listener sees **posted notifications**, not every internal alert or notification-like event inside TraumaSoft. It cannot infer that an MDT assignment was handled unless source-app notifications or a documented explicit acknowledgment signal establish it. If TraumaSoft does not produce a NotificationManager notification, listener-only integration cannot meet the requirement; document the evidence and evaluate lawful supported integration alternatives rather than fabricating a capability.

Eligibility must distinguish new actionable messages/assignments from ongoing service indicators, notification group summaries, duplicate reposts, transient status changes, full-screen incoming calls, and other false positives. Missed call should be distinguished from incoming or active call; the inspected listener contains channel/title/text heuristics that require localization and false-positive testing.

Each source event needs a stable identity incorporating the system notification key and generation/post time as appropriate; later re-posts must not silently reset the reminder timer or cause an earlier removal to acknowledge a newer item. Keep a bounded, privacy-preserving 'recently observed app/channel' diagnostic view to help IT learn actual source identifiers.

---

## Folio 06 — Acknowledgment contract—resolve ambiguities first

The acknowledgment contract is central and historically inconsistent. Older README prose says any source notification removal counts as acknowledgment. The currently inspected `MissedAlertListener.kt` distinguishes user dismissal from source-app cancellation via `NotificationRemovalPolicy.isUserDismissal(reason)` and **retains pending alerts after source-app-initiated removal**, recording `source_removed_retained`. The newer XCover pilot guidance specifically says source-app silent removal does not count as crew acknowledgment and directs IT to the reminder ACK action. Treat this latter behavior as the intended default unless current repo tests/specifications contradict it; update stale README text.

Explicit confirmation paths to test: (a) tapping the combined reminder should acknowledge only the tapped latest item, according to the pilot doc; (b) 'ACKNOWLEDGE ALL' acknowledges the entire pending set; (c) swipe-dismiss of the combined reminder deliberately acknowledges all; (d) direct swipe/clear of a particular source notification may count as user action if Android's reliable removal reason establishes that; (e) source app changing its own notification must not create silent acknowledgment; (f) Android reboot/rebind must not misclassify stale state.

Create a table of event → source identity → pending mutation → audible outcome, and verify it with deterministic tests and on-device traces. When Android removal reasons are uncertain or vendor-modified, classify event as unknown and avoid silently dropping a safety-relevant reminder. Do not track sensitive notification title/text in diagnostics, even if pending-state persistence requires limited private data.

---

## Folio 07 — Core state machine and invariants

Specify state as `DISABLED_BY_POLICY`, `UNREADY_PERMISSION`, `IDLE`, `PENDING_SCHEDULED`, `PENDING_RECOVERY`, and `DEGRADED_TIMING`. Treat these as conceptual states; refactor only if existing code lacks equivalent behavior. Each event (post, update, remove, tap, ACK ALL, swipe, exact-alarm callback, watchdog, listener rebind, boot, package replacement, policy change) must have a deterministic transition and log entry.

Invariants: pending count is never negative; an acknowledged notification generation cannot reappear due to a stale callback; a second notification cannot postpone the first due time; zero pending cancels obsolete future alarms and combined reminder; nonzero pending maintains at most the necessary scheduled alarm/work; after alarm delivery, a new reminder is scheduled or an explicitly recorded degraded state is entered; callbacks are idempotent and safe across duplicate intents and process restart; a failed diagnostics write cannot prevent scheduling/recovery; pending and ACK state are persisted atomically enough to survive normal process death.

Construct a replayable pure or near-pure transition model with test fixtures. Avoid multiple loosely coordinated booleans that make 'one alert then silence' impossible to diagnose. Record before/after state, event category, scheduling result, and why an item was accepted or ignored—without content. Concurrency must be linearizable around the store or appropriately serialized on one executor, and no lock should be held around potentially expensive ZIP/disk/notification operations without justification.

---

## Folio 08 — Notification listener lifecycle

The inspected manifest declares `.MissedAlertListener` with `BIND_NOTIFICATION_LISTENER_SERVICE`. `MissedAlertListener.kt` calls `Notifier.ensureChannel`, schedules `RecoveryJobService`, calls `Scheduler.ensureScheduled`, logs `listener_connected`, and re-syncs `activeNotifications` on listener connection. On disconnection it requests rebind and logs. On posts it evaluates watched apps and missed calls. On removals it uses a reason-based acknowledgment policy and checks notification generation before deletion.

Audit exceptions, permission denial, null `activeNotifications`, connection races, duplicate synchronous callbacks, service recreation, and the exact moment when listeners can be disabled in system settings. Confirm the listener's process is not unexpectedly killed by Knox or Android standby. Listener access cannot be assumed from an Android runtime `POST_NOTIFICATIONS` grant; they are different controls. `POST_NOTIFICATIONS` is for posting the reminder; listener special access permits reading the source notifications.

For recovery, compare persisted pending notifications to currently active notifications, but do not erase retained items simply because the app removed its own notification. Define stale-item reclamation policy carefully and test it. Prove event ordering with wall-clock and monotonic timestamps; use log markers on bind, unbind, sync, recover, and failure. Never continuously poll the listener when callbacks are available.

---

## Folio 09 — Alarm scheduling and Doze constraints

The repository README states that a `setAlarmClock()`-based exact alarm is used to achieve the one-minute reminder interval during Doze, with a visible alarm icon. This must be confirmed against the current `Scheduler` implementation and each target API/firmware. Ordinary `setExactAndAllowWhileIdle` can be throttled; inexact fallback does **not** guarantee a 60-second interval. The inspected manifest requests `SCHEDULE_EXACT_ALARM`; confirm what access is truly available on Android 12+ and how policy affects `canScheduleExactAlarms()`.

Audit reschedule-after-fire first: if the receiver displays one notification but fails before scheduling another, the user sees precisely the observed 'alerts once then stop' symptom. Ensure alarm PendingIntent identity, request codes, mutability flags, cancellation, generation, process-death semantics, time change, timezone changes, battery optimizations, standby buckets and user restrictions are all tested. Use elapsed realtime for interval measurement and preserve wall time for exported chronology.

Avoid claiming perfect one-minute periodicity. Define measurable performance: configured 60 s, actual event-to-fire and fire-to-sound distributions, latency during screen-off/Doze, maximum accepted drift for pilot, and explicit DEGRADED_TIMING status when exact timing is unavailable. Compare alarm delivery with posted-notification delivery and **audible device output** separately. A scheduled/fired alarm is not proof of played sound.

---

## Folio 10 — Recovery watchdog and absence of busy loops

The pilot documentation describes an independent recovery job about every 15 minutes. Its function is detecting missing scheduling and restoring allowed alarms, not generating precise one-minute reminders. Inspect `RecoveryJobService`, `BootReceiver`, `ReAlertReceiver`, and any Android JobScheduler usage. Confirm they survive updates and power cycles in documented ways, avoid duplicate work, and make minimal allocations.

Failure isolation is mandatory: a filesystem exception during logging, status serialization, or diagnostic ZIP construction must not suppress reminder scheduling. A recent commit was explicitly titled 'Isolate alarm recovery from watchdog diagnostic failures'; check implementation and regression tests rather than accepting the title as proof. Recover from listener reconnect, time and timezone change, `MY_PACKAGE_REPLACED`, permission-state changes, and process recreation. Explicit user/Knox force-stop is a special Android state; never promise the app can defeat it by itself.

Low-resource design: use system callbacks and one-shot scheduled work, not a constantly running foreground service, repeating 1-second timer, permanent wakelock or periodic scanning. Each recovery call should be idempotent; if nothing is wrong, it should exit quickly. Include watchdog run count, reason, duration and recovery outcome in diagnostic evidence.

---

## Folio 11 — Notification channel, sound, vibration and routing

Use Android notification channels correctly, including the limitation that users and device policies can freeze channel importance/sound settings once a channel is created. Creating or updating a NotificationChannel does not guarantee it resets user-modified properties. Verify default notification sound, importance, vibration, notification permission, channel blocked state, system notification policy, ringer mode, Do Not Disturb, 'Mute all sounds', Bluetooth, wired audio, separate app sound, and Android notification cooldown where present.

The app may post a successfully logged reminder while producing **no audible tone**. This is a distinct failure from an alarm not firing or a listener not seeing notifications. Test each separately. Do not simply increase notification volume in this app: MMT Volume Guard is the sister application responsible for keeping streams elevated; competing ownership creates races. Display an explicit audio readiness/degraded status and capture available Android signals in IT exports. Never log call/audio content.

On Android 15+ and Samsung variants, confirm repeated notifications remain audible after multiple cycles. Test screen-on, screen-off, kiosk Notification Bar policy combinations, and external sound routes with actual speakers or supported measurement. Do not treat visible reminder posts or synthetic alarms as sufficient clinical acceptance.

---

## Folio 12 — Samsung Knox Manage and KSP enrollment

Deploy a consistently signed in-house APK to managed devices. Assign install, runtime permission grant, and Knox Service Plugin special permissions in the correct sequence. The project README identifies the package as `com.crewalert.missedalert` and listener component as `com.crewalert.missedalert/com.crewalert.missedalert.MissedAlertListener`. Device-wide/managed app rules should cover notification listener access, POST_NOTIFICATIONS on API 33+, alarms/reminders access where relevant, battery optimization allowlist and force-stop blocklist. Validate each on the actual device after policies converge.

Place the app on the kiosk's background/allowed-outside-kiosk list while keeping it off the crews' visible app list. Enable auto-run after install and after update and perform the initial managed Run App so boot broadcasts can work. The README cites a Knox notification-bar restriction that can mute notification tones; validate this precise policy/firmware behavior with current vendor documentation and a pilot. Configure source app notifications too—if SMS/MDT notifications are suppressed upstream, the listener will observe nothing.

Create a reproducible policy checklist with screenshot or export of assigned policy, timestamp applied, actual effective Android permissions, Android build, Knox Manage/KSP versions, and app status logs. Do not assume `adb` is available in production; adb fallback documented in README is a lab/IT-only contingency, not the intended fleet setup. Enforce zero crew intervention.

---

## Folio 13 — Hardware, OS, and kiosk matrix

Target device families: Samsung Galaxy A14, A15, A16, A17 and XCover Pro, subject to actual owned SKU and firmware. The inspected Gradle minimum SDK 29 corresponds to Android 10; the old README incorrectly says Android 13+ in the opening. Confirm each phone's model code, Android release, One UI version, KSP version, kiosk mode and whether manufacturer/firmware permits necessary exact alarm and notification settings. Do not generalize one successful device to every A-series generation.

Build a matrix row for every unique SKU/OS/policy combination: enrollment path; fresh install; initial launch; source SMS alert; source MDT alert; missed call; five repeats; ACK; screen lock; Doze; overnight 24h; reboot; update; force-stop policy; battery optimization; alarm icon; sound route; diagnostics export. Mark each PASS, FAIL, BLOCKED, or NOT RUN, with evidence link and observed timings.

Include Android targetSdk behavior changes, notification permission rules, exact alarm restrictions, background activity launch limits, notification cooldown, app standby, battery saver and device-owner policy interactions. Test actual firmware rather than relying solely on API-level documents. Maintain a known-issues list per model and a safe rollout gate for unseen configurations.

---

## Folio 14 — Managed configuration and policy contract

The README names `extra_packages` (comma-separated package names), `interval_seconds` (default 60, minimum 15) and `max_repeats` (default 0 = unbounded until ACK). Inspect `res/xml/app_restrictions.xml`, `Config`, and policy loading code for schema compatibility and validation. A zero-touch application must start with safe defaults, but defaults cannot silently mask malformed required settings.

Define validation precisely: trim and deduplicate package IDs; bound input length and number of packages; reject malformed package strings; clamp intervals to explicit documented bounds; treat zero max as unlimited; handle negative and overflow values; log effective config version and source; avoid exposing notification contents. Managed policy changes should take effect without showing any crew screen and should not erase pending alerts. Timer changes must be applied deterministically and do not get to postpone an overdue first alert invisibly.

Test no managed config, missing keys, malformed types, stale KSP policy, policy removal, package rename, remote rule update, and source app upgrade. Export assigned versus observed/effective values where possible. No uncontrolled remote configuration channel or listening server should be introduced.

---

## Folio 15 — Persistent store and notification identity

Review `AlertStore` and every persistence call. Inspect pending record schema, per-item generation/postTime, repeat counters, last-seen and ACK tombstones, migration from old schemas, capacity, cleanup, atomicity and synchronization. The inspected listener uses `synchronized(AlertStore)`, `AlertStore.add`, `AlertStore.acknowledgeIfMatches`, `AlertStore.isAcked`, `AlertStore.resetRepeats` and `recordSeen`. Prove that these operations remain correct if multiple notifications arrive and are acknowledged during an alarm callback.

Avoid unbounded growth: bound pending entries, recently seen source/channel catalog, tombstones, logs and transient diagnostic buffers. Any eviction of an unacknowledged item is safety-relevant and must produce a prominent logged degradation. If data is unexpectedly corrupt, do not crash or silently show IDLE; attempt safe parse/recovery and preserve diagnostic evidence. Define retention after device reboot explicitly and reconcile with user expectations; the older README says reboot clears state, whereas the newer pilot talks about persisted state and recovery.

Handle grouped messages, updates with same key but changed postTime, overlapping key reuse, old removal callbacks, launch of source app without notification deletion, and device clock adjustments. Introduce a pure state transition library only if it reduces complexity and can be regression tested.

---

## Folio 16 — Diagnostic event schema

Every significant event should include schema_version; UTC wall timestamp with timezone/offset; monotonic elapsed milliseconds; event_name; app version/versionCode/build SHA (where available); session/boot identifier; device model and API; thread/component; source package/channel (no message body); event or notification identity hash if needed; pending count; repeat counter; scheduled due elapsed time; actual fire elapsed time; permission and policy readiness; success/failure and sanitized error code.

Minimum event families: bootstrap; boot; listener bind/unbind/rebind; source observed/ignored/tracked/updated/removed; ACK path; state-store write/recovery; alarm scheduled/cancelled/fired/rescheduled/fallback; reminder posted or failed; channel status; watchdog evaluation/recovery; policy/permission changes; self-test invocation/result; export begin/end/failure; pruning; storage low; crash/restart markers when determinable. Use stable event vocabulary and version it; avoid logging each heartbeat or flooding device storage.

Log enough to reconstruct a single alert's lifecycle by hashed identifier and times. Preserve classification of 'not observed', 'not scheduled', 'not fired', 'not posted', 'possibly muted', 'not acknowledged', and 'successfully ended'. A log saying only 'alert sent' is insufficient for field failure analysis. Failures in the logger must be isolated from safety-critical notification work.

---

## Folio 17 — Daily logs, retention and resource ceilings

Requirement: automatic local logging organized by day and retained for at least seven days. Use bounded files, safe rollover, atomic file creation/rename, and deterministic pruning. Preserve current day and safety-critical recent entries during rotation; record any drops, truncations or corruption. The recent commit history specifically references diagnostic rotation and retention tests, but re-run and inspect them.

Set an explicit per-day and aggregate byte budget based on actual telemetry collected in pilots; do not pick an arbitrary promise of zero overhead. Include metrics for events/day, bytes/day, peak burst, storage growth, CPU time, maximum export size, and overhead when idle. A seven-day minimum must be balanced with a total cap; define clear policy and expose retention degradation if storage constraints cause earlier deletion. A log level strategy may differentiate routine from detailed diagnostic traces, but critical outcomes must remain recorded by default.

Avoid raw SMS/MDT titles, bodies, names, numbers, tokens, authentication material and location. Sanitization must apply to exception messages and exported device status. Test log writes during low storage, read-only/permission failure, concurrent export and rotation, process death mid-record, and multi-day time change. No silent unbounded memory buffers.

---

## Folio 18 — IT diagnostics and Knox Remote Support workflow

IT must be able to remote into a phone through Knox Remote Support, reach a restricted diagnostic view, review readiness/status, run a controlled synthetic test, and export daily logs into a discoverable user-accessible location. The user specifically wants this as simple as MMT Volume Guard's remote support workflow; avoid a separate server, account or desktop command dependency.

The inspected manifest includes non-exported `StatusActivity` and a DUMP-permission protected `ItDiagnosticsActivity` plus `BootstrapActivity`. Verify the actual IT entry method, accessibility when kiosk is active, and whether support staff can retrieve exported files from Downloads or an agreed share mechanism. Verify no crew-facing access path or sensitive-data exposure. For downloads and ZIP: use temporary incomplete output, finalize only on success, reject partial visible files, validate archive integrity, and include a machine-readable manifest.

A recent set of commits addresses ZIP device-status newline formatting and partial ZIP visibility. Verify file encodings (UTF-8), Windows-friendly newlines or documented handling, timestamped export names, atomic publication, retention and lifecycle concurrent with logger writes. Include a human-readable incident guide in the export so first-line IT can triage without reading source code.

---

## Folio 19 — Export manifest, integrity and privacy

Recommended ZIP layout: `manifest.json` with export schema, app revision, device/OS information, export generated UTC, policy readiness, retention date range and included file hashes; `device_status.txt`; `events/YYYY-MM-DD.jsonl` or established log format; `summary.json` with alert counts/timing and missing prerequisites; and `README_DIAGNOSTICS.txt`. Preserve any already established format unless a migration is justified and tested.

Zip construction must tolerate rotation and concurrent appends safely via snapshot/copy or file lock strategy that does not block alarm delivery. Never export credentials, raw message content or authentication tokens. Redact personally identifying source notification metadata where feasible while maintaining event correlation. Restrict directory access and exported-file visibility as much as platform/tooling allows, document that exported files may be accessible to authorized IT and other permitted device software, and define deletion policy after transfer.

Test export success, unzip integrity, file count, malformed logs, empty logs, 8-day logs, 30-day rollover, storage-full interruption, cancellation, duplicate filenames, Unicode fields, newline correctness, simultaneous events, and process kill. Ensure export never appears successful if CRC/hash check fails or a file is omitted unexpectedly.

---

## Folio 20 — Threat model and security boundaries

Assets: crew operational alert reliability, device availability, diagnostic metadata, release signing identity, deployment policy, and fleet confidence. Attack/failure surfaces: notification listener exposure, exported activity components, broadcast receivers, PendingIntent mutability, managed config parsing, notification actions, file-provider/Downloads export, ZIP entries, CI secrets, third-party dependencies, and exception text.

Inspect manifest exported status and intent filtering. Receivers used only internally should remain not exported unless platform-required; incoming intents should be authenticated/validated. Avoid leaking notification contents into logs, analytics, crash reports or ZIP. Never use insecure world-readable local files; ensure APK contains no keystore or hardcoded credentials. `ItDiagnosticsActivity` is declared exported with `android.permission.DUMP`—verify that this permission restricts access as intended across target OS builds and does not block legitimate IT support workflows. Prefer explicit intents and immutable PendingIntents where allowed.

Device management does not grant carte blanche to bypass platform protections. All exact alarm and notification listener access must be provided by supported admin policy or explicit authorized setup. Document any security/reliability tradeoff. Add automated tests for spoofed broadcasts, wrong ACK IDs, stale generations, malicious managed config and malformed archive paths.

---

## Folio 21 — Reliability-oriented failure taxonomy

Create a deterministic triage tree ordered from source to sound: F0 source app never posted notification; F1 listener access missing/disconnected; F2 listener observed but classification rejected; F3 store write failed; F4 first scheduling failed; F5 alarm scheduled but delayed/not delivered; F6 receiver fired but failed before next schedule; F7 reminder could not post (permission/channel); F8 posted but silent due to ringer/DND/policy/cooldown/audio route; F9 acknowledged incorrectly by source app; F10 pending state lost; F11 Knox disabled/force-stopped app; F12 log/export is incomplete; F13 unknown.

For every class, define observable evidence, root cause hypotheses, deterministic reproduction, correction, regression test and field confirmation. Fix the earliest broken stage before adding new architectural complexity. An audible test tone produced by a manual button proves only a subset of the complete path.

When a field complaint says 'alerts once then nothing', request/inspect a timestamped export with listener posted, alert tracked, first alarm due/fired, next schedule outcome, active pending count, permission/channel state and audio route. Do not infer correct operation from absence of a crash. Distinguish what can be tested in Robolectric from what requires physical Samsung/Knox devices.

---

## Folio 22 — Autonomous testing philosophy and constraints

Automated tests must run unattended in a reproducible environment and produce explicit PASS/FAIL/SKIPPED/NOT RUN status, captured stdout/stderr, duration and artifact. Do not equate successful Gradle invocation with completion of field acceptance. A fully managed Samsung device may require a dedicated test phone and device-farm or lab harness for full-cycle audio/Kiosk testing; if unavailable, report the blocker honestly. Codex cannot remotely press physical buttons or inspect Knox dashboards without granted capabilities.

Layer 1: pure JVM state-machine and configuration tests. Layer 2: Robolectric Android framework tests for listener parsing, receiver dispatch, persistence, channels, exports where supported. Layer 3: Android instrumented tests/emulator for service/intent/permission behaviors. Layer 4: physical Samsung device tests for real Doze, kiosk, audible output, app launch, hardware and policy differences. Layer 5: monitored pilot with real SMS/MDT and incident response.

Build a test harness that can replay synthetic notification events with fake time and deterministic alarm delivery in process. Synthetic unit-test success must be clearly labeled and never presented as proof of actual source notifications, physical sound, or 60-second Doze delivery.

---

## Folio 23 — Unit and property test plan

Test core state invariants under deterministic event sequences. Fixtures: one SMS item; MDT re-post; duplicate listener callback; two pending packages; same notification key/new postTime; old-generation removal; source-app cancellation; user dismissal; ACK one; ACK ALL; swipe combined reminder; reboot; app upgrade; inexact alarm fallback; policy changed mid-alert; null extras; malformed channel; malformed timestamp; missed call vs incoming call.

Property tests should generate randomized post/update/remove/ACK/restart sequences and assert that count cannot go negative, acknowledged generations cannot resurrect, and one pending source remains pending unless an approved acknowledgment path removes it. Run thousands of sequences under CI with fixed seed on failure. Fuzz inputs such as long Unicode titles, invalid package lists, inconsistent time/clock jumps, JSON corruption, and duplicate alarm intents.

Use tests as executable specifications for ambiguous contracts. Record actual named test classes and results in the ledger. If tests are flaky due to Robolectric global state or cached singleton settings, isolate the state or control reset and add regression coverage; recent history indicates retention-test isolation has already been a focus.

---

## Folio 24 — Android integration test plan

Build instrumented test fixtures that synthesize StatusBarNotifications only where supported without pretending to generate external source-app activity. Assert notification channel creation, posting permissions, Receiver→Scheduler linkage, exact alarm capability handling, BootReceiver behavior and persistence after process recreation. Verify PendingIntent identity and cancellation semantics on every API level available to CI.

Use an emulator as a broad Android compatibility gate, not as proof of Samsung policies. Automate permission toggles, app standby, process kill, time change, timezone change, package update, reminder ACK and export under controlled conditions. Capture filtered logcat plus app JSONL diagnostic export; reconcile emitted events with expected transitions. Where the emulator cannot simulate a real NotificationListenerService or source-app notifications reliably, mark the test as limited.

Generate test reports with an exact Gradle command, API/device image, dependency checksums, timestamp, commit SHA and whether execution was local, CI, emulator or hardware. Keep artifacts bounded and avoid exceeding GitHub Actions storage quotas.

---

## Folio 25 — Physical-device acceptance and sound proof

Minimum pilot test: five or more consecutive reminders for each source family (Google/Samsung SMS as applicable, TraumaSoft MDT, missed call), on each selected fleet model, while the phone is in the actual Knox kiosk. Repeat with screen unlocked, locked, during Doze, after reboot, after app update, after 24h idle, and with two simultaneous alerts. Log expected and observed fire times and record whether sound/vibration was physically heard or independently measured. Record source app notification behavior and ACK action outcome.

Doze verification demands a genuine idle or supported test command plus a real device observation; a one-minute emulator timer is not enough. Check multiple-volume/routing settings, notification cooldown on Android 15+, DND, silent, Bluetooth/wired routes, and Knox Notification Bar policy. Note if IT intentionally forbids problematic controls through policy and confirm effective enforcement.

Clinical readiness gate: no missed source classifications in the tested scenario; no unexplained one-shot failures; correct acknowledgment and concurrency; documented timing; audible delivery in operational mode; export available; rollback APK signed with the same key; responsible pilot owner signs off. Unsupported OEM behavior or silent routing should block mass rollout even if source code tests are green.

---

## Folio 26 — Performance, memory and power budgets

Resource constraints are explicit project goals, but no verified numerical budget was supplied. Codex should measure actual idle CPU, app RSS/PSS, heap, wakeups, jobs, alarm counts, bytes written/day, battery drain under 24h idle and under repeated alerts. Choose empirical thresholds based on baseline Samsung devices and approval before treating a number as a hard pass/fail contract.

Default architecture should be event-driven using one listener, a one-shot reminder alarm, small persistent state, bounded logging, and a sparse recovery job. Avoid polling source apps, permanent socket connections, large in-memory caches, busy timers, unused libraries, foreground services with unnecessary notifications and expensive parsing in callback threads. Log write failures should not block the listener. Precompute stable regexes/classification rules only where measurable; avoid premature optimization that harms correctness.

Benchmark cold start, first notification classification, store mutation, alarm-to-post latency, recovery time and ZIP export under realistic logs. Use allocation profiling and memory leak checks during 24h pilot. Distinguish process memory (which Android may reclaim) from battery impact. Record before/after metrics for every optimization rather than promising negligible consumption.

---

## Folio 27 — Build, dependencies, and signing controls

Use the actual Kotlin Gradle build. The inspected `app/build.gradle.kts` uses `compileSdk=36`, `targetSdk=36`, `minSdk=29`, AGP 8.13.2, Kotlin 2.3.10, JDK 17, JUnit/Robolectric and conditional environment-driven release signing. Confirm Gradle wrapper version, Android SDK platform/build tools, dependency repositories and reproducible execution. Do not repeat the older README's obsolete 'Gradle 8.7' claim without checking.

Signing variables observed: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never print them, commit them, embed them into logs or upload signing secrets into test artifacts. Verify release APK signature and certificate continuity before distributing an update; incompatible signatures can trigger Knox installation failures or leave multiple versions. Distinguish unsigned/locally signed debug build, locally tested release candidate, and trusted fleet-signed APK.

CI should run compilation, unit tests, static checks where available, manifest/exported checks, ZIP export tests and artifact integrity checks. Avoid unnecessary build artifacts; set retention and prevent quota exhaustion. A passing build on CI still does not prove kiosk delivery, notification listener access, sound or Doze behavior.

---

## Folio 28 — Continuous integration and release gates

Define build workflow stages: checkout pinned SHA → JDK/SDK provisioning → dependency verification → compile debug/release as allowed → JVM/Robolectric tests → lint/static analysis → package metadata/signature validation → test reports → bounded artifact publication. Record GitHub run ID and linked commit in the ledger. Audit actual existing workflows before introducing a new one.

Prevent merging failed checks. For repository preference, integrate to `main` without spawning many persistent branches; use short-lived isolated branches only if necessary for parallel safety and existing protection rules. Never force-push just to avoid branches. An automatic release should only be generated if signing secrets and gates are valid. Production deployment needs a separate explicit approval and device acceptance.

For each change, Codex must explain diff intent, unit tests run, regression risk, versionCode/versionName impact, signing status, kiosk policy changes, documentation updates, rollback path and unresolved field tests. If GitHub Actions quota blocks uploads, run non-artifact verification and report exact blockage; do not mislabel omitted tests as passed.

---

## Folio 29 — Mainline workflow, commits, rollback

The user's preference is one main development line and no uncontrolled proliferation of divergent APK versions. Respect the default branch, required checks, and GitHub security rules. Snapshot the current main SHA before edits; create small, reversible commits with linked test evidence. Integrate only when tests pass and no fatal requirements regress. Do not erase pilot history; determine whether `pilot/xcover-pro-reminder-recovery` is already merged, stale or contains unintegrated work using a real compare operation.

Maintain a release inventory: versionCode/versionName, commit, APK SHA-256, signing certificate digest, targetSdk/minSdk, policy revision, supported tested device matrix, build/test result and deployment date. Rollback is not merely `git revert`: Android package downgrade limitations, persistent store schema and signing continuity may complicate device rollback. Define a safe release sequence and verify restore on a pilot phone.

After a failure, reproduce, isolate root cause, add regression test, implement minimal patch, rerun the full relevant suite, then update the dossier status and runbook. If real-device evidence remains unavailable, mark code-ready only, not fleet-ready.

---

## Folio 30 — Operational readiness status screen

IT needs a concise dashboard with distinct indicators, not a fake single green READY state. Include package/version/build, device model/OS, listener special access, notification post permission, channel importance/audio state, exact alarm eligibility, battery optimization, kiosk/background policy (when detectable), recent listener connection, last source seen per watched package, last alert tracked, current pending count, next alarm due, last fired, last posted, last audible validation (if manually measured), watchdog last run, free storage/log retention/export status, recent errors and current degradation reason.

Status should distinguish UNKNOWN from OFF and BLOCKED from FAIL. Android APIs may not disclose whether Knox policy is effectively blocking sound or whether a notification was audibly played; these must be labeled 'not directly observable' rather than inferred healthy. Include an IT-only 'copy status' and export diagnostics action if supported. The interface must not appear at every kiosk launch.

Review status serialization stability and newline formatting against recent ZIP commits. Keep screen lightweight with on-demand reads; no permanent activity or continuous redraw. Never expose message bodies, crew phone numbers or location. Include quick troubleshooting instructions and the exact next evidence needed for each missing prerequisite.

---

## Folio 31 — Synthetic self-tests and truthful results

Provide independent test stages where feasible: config parse; listener binding status; store roundtrip; alarm scheduled callback; reminder posted; sound-capability inspection; diagnostic write/rotate; ZIP export integrity; reboot recovery evidence. Each must have a test ID, start/finish, purpose, expected observation, observed result and PASS/FAIL/INCONCLUSIVE/NOT RUN verdict. A synthetic notification posted by the app itself cannot prove that a source app's notification is being observed; document this limitation directly.

Design tests to be safe in operational contexts: no flood of loud alerts without controlled IT action, no fake dispatch/clinical content, no permanent pending items, no deletion of existing real alerts, and no reset of crew ACK state. Prefer isolating test identifiers and temporary stores. If some tests need a remote IT-triggered action, make that one action simple and ensure all follow-up cleanup is automatic.

A self-test should export raw evidence and an operator-readable summary so Codex can correlate it with expected state transitions. Success requires test assertions, not just no exception. Run failure injection on nonproduction devices only.

---

## Folio 32 — Knox Remote Support diagnostic runbook

When a crew reports a missing repeat: (1) IT records device model, firmware, date/time/timezone and source app involved; (2) connect through Knox Remote Support; (3) access IT status using documented authorized route; (4) verify notification listener, POST_NOTIFICATIONS and channel, alarm permission, battery/force-stop and source notifications; (5) export diagnostic ZIP; (6) confirm the resulting filename and integrity; (7) correlate reported message time to source_observed→alert_tracked→alarm_scheduled→alarm_fired→reminder_posted→next_alarm; (8) classify one of failure taxonomy F0–F13; (9) document repair/policy change; (10) repeat controlled test and capture evidence.

If source app itself never posted a notification, do not blame the reminder scheduling code. If the source was tracked but the next alarm was absent, inspect receiver error and scheduling path. If posted but silent, inspect Knox Notification Bar, channel, DND, cooldown, audio route and Volume Guard status. If pending disappeared, inspect ACK reason and generation.

Preserve original exported evidence before modifying device policies or reinstalling. Respect operational procedures: do not remote-control a device during active patient care without authorization. Do not share logs publicly or add personal message content to GitHub issues.

---

## Folio 33 — Interoperability with MMT Volume Guard

MMT Volume Guard and Missed Alert are separate APKs with separate responsibilities. Volume Guard attempts to maintain permitted stream volumes and normal ringer state; Missed Alert listens, tracks and posts repeats. Device Provisioner is another separate MMT app for one-tap initial sound/volume setup; do not collapse these repositories or duplicate configuration owners casually.

Test both apps installed simultaneously and under kiosk. Compare their notifications, package IDs, battery exemptions and policies. Avoid feedback loops: Missed Alert should ignore its own reminder notification, and Volume Guard's volume correction should not trigger endless re-posting or channel recreation. Confirm notification/system sounds use the actual Samsung route. A max volume readback does not prove Do Not Disturb, Bluetooth output or notification cooldown are benign.

If integration is ever added, prefer observability through supported intents/status rather than shared mutable internal files or background polling. No cross-app integration is required merely to complete core reliability acceptance. Maintain separate release inventories and diagnostics exports.

---

## Folio 34 — TraumaSoft MDT-specific uncertainty plan

The developer may not have access to TraumaSoft internal notification payload formats or API specifications. The project must accommodate that honestly. Observe actual notifications on a managed pilot phone and use available package name, channel, category, flags, postTime, key and removal reasons. Do not store patient details or call content. Add a robust operator-visible 'packages/channels recently seen' view so IT can identify which installed MDT app variant is actually posting.

Test assignment notification arrival, repeated assignment updates, source app dismissal, MDT user acknowledgement behavior, grouped updates and app relaunch. Determine whether source application cancels notifications automatically and whether such cancellation should preserve Missed Alert pending state. Ensure the first reminder timer is not reset each time MDT refreshes a notification.

If the MDT app provides in-app only alerts, document the hard technical limitation of NotificationListenerService, identify supported vendor options, and refrain from claiming a fix without an approved integration. The goal is reliable crew alerts, not merely an APK that runs.

---

## Folio 35 — SMS and missed-call-specific uncertainty plan

SMS apps vary by manufacturer, Android version and user-selected default handler. Google Messages, Samsung Messages and AOSP MMS should be tested independently where deployed. Test one incoming message, multiple messages in one conversation, updated conversation summary, read-on-another-device, source cancellation, locked screen, muted conversation, blocked notification channel and messages arriving in quick succession. A conversation grouping should not produce repeated duplicate alarms or suppress a genuinely new item.

Missed calls are detected from phone/dialer/Telecom notifications, not through indiscriminate phone-state surveillance. The inspected listener checks dialer package, CATEGORY_CALL, fullScreenIntent and 'missed' in title/text/channel. Audit localization (Spanish or other device locale), OEM phrasing, incoming call notifications and spam/voicemail. Prefer channel/category/known provider evidence over text matching when feasible. Test that active calls are not treated as missed calls.

Keep phone numbers, sender identities and message bodies out of exported diagnostics. Do not seek READ_SMS or call log permissions unless a separate, explicitly approved architecture requires them.

---

## Folio 36 — Concurrency, race and exception audit

Audit every lock and callback crossing `MissedAlertListener`, `AlertStore`, `Scheduler`, `Notifier`, `AckReceiver`, `ReAlertReceiver`, `BootReceiver`, `RecoveryJobService`, IT diagnostics and managed policy listener. Check lock ordering and shared singleton lifetimes. Reproduce pending item addition while an alarm fires, ACK while another alert arrives, process kill between store write and schedule, disk write failure during watchdog, notification post exception followed by recovery, two simultaneous ACK intents, and double-received alarm callback.

Exception handling should preserve safety: log sanitized event and attempt the appropriate recovery rather than swallow silent errors. Do not catch `Throwable` broadly unless needed for last-resort crash protection with carefully bounded behavior. Avoid applying expensive filesystem operations inside synchronized critical sections. Keep callbacks short and idempotent; use a small serial executor only if required for correctness.

Model fault injections in tests. A successful one-off run is not adequate: schedule randomized stress sequences with replay seeds and compare final state to the reference transition model. Track flakiness and failures in the ledger; never delete a failing test to make CI green.

---

## Folio 37 — Clock, reboot, and process lifecycle correctness

Handle `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, and exact-alarm permission state changes as declared in the inspected manifest. Differentiate wall-clock adjustments from elapsed-monotonic scheduling. Device reboot resets monotonic time; persisted due-time reconstruction needs a documented policy. The project README's claim that reboot clears pending state conflicts with newer persistence/recovery requirements and must be reconciled against current code and pilots.

Tests: app process killed but not force-stopped; normal reboot before first repeat; reboot after 3 repeats; time rolled forward/back; timezone changed; OTA update; app replacement; listener access revoked then regranted; exact alarm permission revoked; kiosk exit/reenter; update while alert pending. For each, specify whether pending items are restored, rediscovered from active notification shade, intentionally cleared, or retained. Never silently convert state loss into 'all acknowledged'.

Use a boot/session marker in logs. Compare last persisted event to first post-restart event; emit a recovery-gap diagnostic. On force-stop, Android intentionally suppresses package activity until restarted: instruct Knox IT on supported recovery rather than claiming app-level self-resurrection.

---

## Folio 38 — Documentation system and consistency checks

Keep `README.md` as concise installation/architecture orientation, `docs/MASTER_ENGINEERING_DOSSIER.md` as the complete handoff, `docs/FEATURE_STATUS.md` as a per-feature evidence table, `docs/ROADMAP.md` as prioritized tasks and acceptance gates, `docs/USER_MANUAL.md` as Knox/IT operator steps, `docs/TEST_MATRIX.md` as device/API results, `docs/CHANGELOG.md` for release differences, and `docs/INCIDENT_RUNBOOK.md` for diagnostic triage. Reuse existing documents if present—do not create duplicates merely to satisfy names.

Codex must update documentation in the same commit as behavior changes. Reconcile stale README statements: Android 13+ vs current `minSdk 29`; 'not yet compiled' vs actual CI result; source-notification cancellation equals ACK vs current code's retained behavior; recommended build tools vs the actual wrapper. Add links to GitHub runs, commits, APK hashes and export samples. A documentation line saying 'works' requires proof category.

At each handoff update, include exact current SHA, repository tree high-level map, current PR/issues, outstanding decisions, owner, current blocker, and a single next unblocked task. No infinite narrative that obscures state. Preserve prior incident investigations and failed approaches in an appendix or ledger.

---

## Folio 39 — Priority roadmap and dependency order

Priority P0 (truth and reproducibility): inspect full tree/default branch/pilot branch diff, read tests/workflows/recent commits, run baseline build and tests, document actual failing/passing results, and reconcile contradictory docs. P1 (core reliability): trace listener→pending store→exact alarm→receiver→next alarm→notification post→ACK; reproduce one-shot failure; add a regression test; fix root cause. P2 (managed runtime): audit Knox bootstrap and effective permissions, Doze/exact-alarm state and force-stop limitations. P3 (diagnostics): validate bounded daily logs, at-least-week retention, robust ZIP export and IT usability. P4 (coverage): cross-app cases, missed calls, concurrent items, reboot, app update and API compatibility. P5 (physical pilot): device-specific sound and timing, 24h run, acceptance signoff. P6 (release): version, sign, verify, distribute gradually and monitor.

Each task must include task ID, scope, files, expected tests, acceptance criteria, evidence source, dependence/blocker, start/end SHA and rollback consideration. Codex should select the highest-priority unblocked task, complete it through test and documentation, then continue to the next one within the current coding session. Do not spin in analysis/research indefinitely once a reproducible code defect is identified.

Do not conflate 'code complete' with 'fleet operational'. Overall project status is the minimum of code correctness, tested integration, kiosk policy readiness, physical audible verification, operational logging and safe rollout.

---

## Folio 40 — Codex operating protocol and long-session resume

At each session start: checkout/inspect repository, default branch, current SHA, dirty tree, open PRs/issues, CI logs, README, dossier and latest ledger entry. Compare any pilot branch; do not discard another agent's work. Produce a compact plan of the next 3–5 independently verifiable tasks, then execute the first. Commit in small increments after tests. After a test fails, investigate root cause, add/adjust regression and rerun. Continue without repeated 'may I proceed' prompts until a genuine gate appears.

End of each bounded execution: write/commit an updated `docs/CODEX_RESUME.md` containing timestamp, start/end SHA, work completed, exact test commands and results, changed files, unresolved failures, blockers, next 3 tasks with file paths, how to reproduce, and risks. Schedule additional Codex tasks or CI jobs only through actual available platform controls; never claim work continues after the session ends unless automation really exists.

If multiple agents are available: partition by non-overlapping files/functions (core scheduling vs diagnostics vs docs/test harness), appoint an integration owner, gate each change with shared tests and serial integration. Avoid simultaneous edits of manifest, Gradle config, state store or release workflows without explicit coordination. Security and resource limits apply to every agent.

---

## Folio 41 — Decision register and unresolved policy questions

Record explicit decisions, not guesses. D-001: default reminder repeat interval 60 s (README); verify effective managed policy. D-002: max_repeats 0 means continue until ACK. D-003: managed background operation with no crew launch/icon. D-004: app is a normal managed APK, not a privileged firmware app. D-005: keep source message body out of diagnostics. D-006: isolated responsibility from MMT Volume Guard and Device Provisioner. D-007: user prefers integration on `main`. D-008: current listener code retains source-app removals unless user dismissal; reconcile docs and field expectation. D-009: Android 10+ minimum currently declared; verify test coverage.

Open question Q-001: exact ACK semantics for source swipe vs app auto-cancel on each firmware. Q-002: post-reboot persistence/clear policy. Q-003: actual TraumaSoft notification classes/channels across installed MDT versions. Q-004: allowed maximum reminder delay and minimum demonstrated audible performance. Q-005: kiosk policy settings supported by each enrolled device model. Q-006: physical-device test harness availability. Q-007: device retention size cap consistent with seven-day requirement. Q-008: IT-only diagnostics entry in Knox Remote Support. Q-009: release signing and rollback owner.

Codex may choose safe reversible implementation defaults, but cannot silently decide an operationally consequential policy. Put each unresolved choice in the ledger with proposed options, risk and evidence needed; do not block unrelated work.

---

## Folio 42 — Acceptance checklist and definition of done

The project is not done because files exist or an APK builds. Required acceptance: clean build from documented toolchain; passing core unit and integration suites; no unresolved critical races; valid release signature; correct listener observations on actual supported source apps; five repeat cycles minimum in each pilot mode; ACK one and ACK all work under concurrency; no silent source-autocancel; recovery after process death, reboot and upgrade where promised; documented fallback when exact alarms unavailable; accurate UI status; seven-day daily logs; ZIP integrity and IT retrieval; bounded resource measurements; no unauthorized sensitive content leakage; successful managed kiosk launch and no crew-visible startup activity.

Every device/OS row needs evidence and a disposition, not universal PASS extrapolated from one phone. 'Not tested' is distinct from 'failed'. No clinical production deployment until a responsible IT/operations owner accepts test limitations and proves audible reminders in real operating mode. Establish an escalation procedure for devices with listener access denied, DND routing or otherwise impossible sound delivery.

Deliverables: merged code; reproducible build instructions; signed release candidate as authorized; test reports; pilot device matrix; engineering ledger; operator manual; incident runbook; living dossier; versioned changelog; SHA/signature inventory. Add explicit rollback and monitoring for the first staged group of phones.

---

## Folio 43 — First Codex session: exact initial execution checklist

1. Open `DevTunaSoft/missed-alert` and record exact `main` SHA. Enumerate tracked source, tests, docs and workflows. Compare `pilot/xcover-pro-reminder-recovery` to `main` and identify unmerged work. 2. Run baseline Gradle tasks available in repo; capture failure details, not guesses. 3. Read `Scheduler`, `ReAlertReceiver`, `AlertStore`, `Notifier`, `RecoveryJobService`, `EventLog`, `BootReceiver`, `AckReceiver`, `Config`, Kotlin classes and manifest. 4. Build a trace table for a single incoming SMS: listener connected → tracked → stored → scheduled due → first fired → posted → next scheduled → second fired. 5. Identify precisely where repeat continuity can break and enumerate exceptional paths. 6. Write regression for the most reproducible break. 7. Implement the smallest correction and run all affected tests. 8. Update stale README claims and ledger. 9. Repeat for simultaneous SMS+MDT and explicit ACK. 10. Validate diagnostics export in automated tests.

Stop destructive releases or policy modifications without required access/approval. If blocked by absent device, do as much code-level testing as possible and record the exact physical test script and expected evidence. Do not re-implement currently working logging, ZIP export or Knox bootstrap because earlier conversation incorrectly suggested they were missing.

Provide concise checkpoint report after each commit: SHA, changed code, passed/failed tests, status, next action. Continue through the backlog in the active session without repeated permission requests.

---

## Folio 44 — Living ledger template and handback report

Create ledger record fields: `task_id`, `started_utc`, `ended_utc`, `baseline_sha`, `end_sha`, `branch`, `objective`, `cause`, `files_changed`, `tests_commands`, `test_results`, `device_evidence`, `artifact_links`, `permissions_or_policy`, `memory_power_delta`, `privacy_impact`, `risk`, `rollback`, `open_questions`, `next_action`. Append a record per meaningful change; avoid rewriting history. Distinguish code-reviewed from code-executed and device-tested.

Handback status should have one-line gate state per domain: BUILD, UNIT, INTEGRATION, PHYSICAL SAMSUNG, KNOX, DOZE, AUDIO, LOG RETENTION, EXPORT, SECURITY, RELEASE. Allowed statuses PASS, FAIL, BLOCKED, NOT RUN. Attach links to evidence and describe exact limitations. Then give the next implementable task with file paths and necessary tests.

The dossier is a living artifact; each subsequent Codex handoff should be self-contained, not depend on this ChatGPT conversation. Preserve 'why this exists' in opening sections and 'where to resume' at the end. Record important deviations to project requirements with rationale and a test; do not let seemingly cosmetic edits silently change ACK, alert source or kiosk behavior.

---

## Folio 45 — Appendix: canonical events and state transition assertions

Proposed trace sequence for a normal alert: `listener_connected` → `alert_tracked` → `alarm_scheduled` → `alarm_fired` → `reminder_posted` → `alarm_scheduled` (repeat) → `alarm_fired` → `reminder_posted` → `ack_*` → `alarm_cancelled` → `reminder_cancelled`. Actual event names may differ; map them to current EventLog rather than renaming everything gratuitously.

Assert `POST` before `ACK`: pending increments once and due time established; `POST duplicate`: pending count and earliest due unchanged; `POST second`: pending increases without postponement; `SOURCE AUTO CANCEL`: pending retained unless a documented acknowledgment signal exists; `USER SWIPE SOURCE`: only matching generation removed if policy says ACK; `ACK ONE`: only selected generation removed; `ACK ALL`: empty store and no scheduled repeats; `ALARM FIRE`: subsequent alarm attempted and logged when pending remains; `REBOOT/REBIND`: consistent rediscovery/recovery; `EXACT ALARM OFF`: explicit fallback/degradation; `LOG WRITE FAIL`: notification path still functions.

Counterexamples that must never pass tests: 1 alert posts once but future due absent without explicit degradation; every update pushes next fire 60 seconds later; source app background sync implicitly ACKs; a late old removal deletes new notification; stale PendingIntent dispatch deletes current items; a ZIP file is visible as complete before it is closed and checksummed.

---

## Folio 46 — Appendix: sample engineering evidence artifacts

For a reproducible failure, capture `case_id`, device model/SKU, Android/API/One UI build, Knox/KSP/app policy revision, source package/channel, app version/commit, interval config, notification posted timestamp, source eligibility decision, alarm due clock, fired timestamp, reminder posted timestamp, subsequent due, reason codes, screenshot/IT export path and tester observation of sound. Avoid patient details.

Example evidence (illustrative, NOT a real test result): `CASE-SMS-DOZE-001 | model=<model> | API=<api> | app=<sha> | observed source at 12:00:00 | scheduled 12:01:00 | fired 12:01:02 | posted 12:01:02 | rearmed 12:02:02 | sound=NOT_VERIFIED`. This format must not be mistaken for observed success.

Unit-test artifact: JUnit XML with suite, test and failure; CI job URL; SHA. Build artifact: Gradle log and APK SHA-256 plus signing certificate fingerprint (without private key). Physical test artifact: timestamped diagnostic ZIP plus qualified observer note, captured in an approved secure location. Incident record: problem, root cause, fix, regression, policy update and follow-up result.

---

## Folio 47 — Appendix: Codex master takeover directive

You are the lead engineering agent for the `DevTunaSoft/missed-alert` Android repository. Treat this entire dossier as controlling requirements and a fallible historical snapshot; inspect the code and latest GitHub state before working. Your objective is a zero-touch, Knox-managed, low-resource SMS/TraumaSoft MDT/missed-call reminder that reliably repeats until explicit ACK, provides IT-accessible seven-day daily logs and ZIP exports, and is supported by autonomous reproducible tests plus honest Samsung pilot evidence.

Do not ask permission for routine implementation, tests, fixes, documentation or small reversible engineering choices. Preserve existing work. Diagnose root cause, write regression, patch, rerun, log evidence, commit verified work, and continue to the next unblocked task. The immediate priority is verifying full repeat continuity (especially rescheduling after first alarm) and acknowledgment correctness. Then harden Doze/recovery, permissions, channel sound, Knox kiosk operation, diagnostics and resource behavior.

Honor `main` as the integration destination; avoid permanent branch clutter, protect secrets/signing and respect repository rules. Do not make unsupported guarantees that a notification was heard or that Android permits unstoppable background execution. Maintain `docs/MASTER_ENGINEERING_DOSSIER.md`, a concise current-status/ledger and resumption instructions. At session end or real blocker, hand back exact SHA, tests, failures, artifacts, policy assumptions and next action. Never represent code-ready as production-ready.

---

## Appendix A — Formal cross-layer acceptance catalog

Each row is a test case specification, **not** an executed result. Codex shall convert it to actual tests/scripts, record PASS/FAIL/BLOCKED/NOT RUN, attach evidence and track model/API/policy coverage.

| ID | Scenario | Acceptance observation | Evidence required | Current status |
|---|---|---|---|---|
| SMS-01 | Google Messages new unread SMS | Listener sees posted; first timer and five repeats; ACK stops | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| SMS-02 | Samsung Messages new unread SMS | Equivalent behavior on available model | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| SMS-03 | Multiple SMS within one conversation | No duplicate runaway jobs; new item not lost | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| SMS-04 | Message read remotely | Source auto-cancel does not silently ACK | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| MDT-01 | TraumaSoft new assignment notification | Tracked and repeated; package/channel evidence | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| MDT-02 | TraumaSoft notification update/repost | First due not postponed | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| MDT-03 | TraumaSoft removes its notification | Retained pending until approved ACK | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| CALL-01 | Missed call | Tracked and repeated | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| CALL-02 | Incoming active call | Never classified as missed | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| ACK-01 | Tap latest reminder | Only intended alert acknowledged | Automated test + event trace | NOT RUN in dossier preparation |
| ACK-02 | ACKNOWLEDGE ALL | All pending clear; scheduler stopped | Automated test + event trace | NOT RUN in dossier preparation |
| ACK-03 | Swipe combined reminder | Documented all-items behavior | Automated test + event trace | NOT RUN in dossier preparation |
| ACK-04 | Late removal old generation | New generation retained | Automated test + event trace | NOT RUN in dossier preparation |
| ACK-05 | Double ACK intents | Idempotent | Automated test + event trace | NOT RUN in dossier preparation |
| ALM-01 | One-shot alarm continuation | Second, third, fifth fires verified | Automated test + event trace | NOT RUN in dossier preparation |
| ALM-02 | Exact alarm denied | Recorded degraded timing/fallback | Automated test + event trace | NOT RUN in dossier preparation |
| ALM-03 | Duplicate receiver callbacks | No duplicate schedules or lost state | Automated test + event trace | NOT RUN in dossier preparation |
| ALM-04 | New item while timer pending | Original due preserved | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-01 | Listener rebind | Active notifications resync; state consistent | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-02 | Process death | Recovery without manual crew launch | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-03 | Reboot | Pending semantics documented and proven | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-04 | App update | Permissions, alarms, and state restored appropriately | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-05 | Time/timezone change | No pathological delay or duplicate | Automated test + event trace | NOT RUN in dossier preparation |
| LIFE-06 | Force-stop | Document inability/recovery path | Automated test + event trace | NOT RUN in dossier preparation |
| POL-01 | Knox kiosk hidden background app | No intrusive UI; effective listener access | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| POL-02 | Notification post permission denied | Explicit readiness failure | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| POL-03 | Source notifications disabled | Observable F0 prerequisite failure | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| POL-04 | Kiosk notification bar disallowed | Audible behavior measured, not assumed | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| POL-05 | Battery optimized vs allowlisted | Real Doze timing recorded | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| AUD-01 | DND/silent/mute-all | Correct degraded status | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| AUD-02 | Bluetooth/wired route | Actual output checked | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| AUD-03 | Android notification cooldown | Five repeat sounds observed | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| LOG-01 | Daily rotation and 8+ day retention | Minimum seven days maintained under budget | Automated test + event trace | NOT RUN in dossier preparation |
| LOG-02 | Low storage during log write | No alarm failure; clear degradation | Automated test + event trace | NOT RUN in dossier preparation |
| ZIP-01 | Export with active logger | Complete CRC-valid ZIP | Automated test + event trace | NOT RUN in dossier preparation |
| ZIP-02 | Process death during export | No partial completed-looking ZIP | Automated test + event trace | NOT RUN in dossier preparation |
| ZIP-03 | Status newline/Unicode | Round-trip correct content | Automated test + event trace | NOT RUN in dossier preparation |
| SEC-01 | Forged internal broadcast | No arbitrary ACK or sensitive export | Automated test + event trace | NOT RUN in dossier preparation |
| SEC-02 | Malformed managed config | Safe bounded defaults and error | Automated test + event trace | NOT RUN in dossier preparation |
| PERF-01 | 24h idle device | Memory/battery/wakeups measured | Physical Samsung + diagnostics | NOT RUN in dossier preparation |
| PERF-02 | High notification burst | No runaway allocations or data loss | Physical Samsung + diagnostics | NOT RUN in dossier preparation |

## Appendix B — Defect-to-test mapping

Every production defect must generate or update at least one regression. Map `one alert then silence` to ALM-01 + LIFE-02 + LOG-02; `permission OFF` to POL-02; `silent under kiosk` to POL-04 + AUD-01; `MDT auto removal` to MDT-03 + ACK-04; `update shifts due` to MDT-02 + ALM-04; `partial ZIP` to ZIP-02; `infinite memory growth` to PERF-02. Attach exact incident ID, cause, code line, regression test and post-fix run ID. Confirm tests fail before fix where feasible.

## Appendix C — Device campaign grid

For each device variant in the actual fleet, execute `fresh install`, `policy convergence`, `SMS x5`, `MDT x5`, `missed-call x5`, `screen off x5`, `Doze x5`, `multi-alert ACK`, `reboot`, `upgrade`, `24-hour idle`, `ZIP export`, `audio routing` and `rollback trial`. Create one row per model/firmware/KSP/policy combination. The following entries are **planned** rather than demonstrated: A14, A15, A16, A17, XCover Pro. Exact Android/One UI versions must be collected from devices; do not guess.

## Appendix D — Sources inspected and known omissions

**Inspected via linked GitHub repository on 2026-11-12:**
- https://github.com/DevTunaSoft/missed-alert/blob/main/README.md
- https://github.com/DevTunaSoft/missed-alert/blob/main/docs/XCOVER_KNOX_PILOT.md
- https://github.com/DevTunaSoft/missed-alert/blob/main/app/src/main/AndroidManifest.xml
- https://github.com/DevTunaSoft/missed-alert/blob/main/app/build.gradle.kts
- https://github.com/DevTunaSoft/missed-alert/blob/main/build.gradle.kts
- https://github.com/DevTunaSoft/missed-alert/blob/main/settings.gradle.kts
- https://github.com/DevTunaSoft/missed-alert/blob/main/app/src/main/java/com/crewalert/missedalert/MissedAlertListener.kt
- Recent commits around `fe19287723293c017ed9fa595863908941edf47d`.

**Not fully inspected:** entire repository tree; all Kotlin source; all tests; workflows and their execution; issue/PR details; actual APK and signing certificate; Knox tenant and device policy; Samsung pilot diagnostics; audible field test. Any command or Codex report that implies these were verified by this dossier is inaccurate. This package is a handoff specification for further work, not a completed code audit.

## Appendix E — Immediate Codex input (copy verbatim)

> Read `docs/MASTER_ENGINEERING_DOSSIER.md` in full. Inspect the real repository and refresh snapshot facts. Follow its priorities and execute successive small, verified engineering cycles. Do not ask approval for routine work. Start by tracing and testing the full notification→pending→alarm→receiver→next alarm→reminder→ACK lifecycle to eliminate one-shot reminder failures. Preserve existing ZIP logging and Knox bootstrap implementation, and reconcile stale README facts. Commit tested improvements to the designated main development line under existing protections. Maintain a cumulative ledger, concise resume instructions, and honest PASS/FAIL/BLOCKED/NOT RUN status. Never claim real Samsung/Knox/Doze/audio proof unless a physical test actually ran. Continue until completion or a genuine external blocker, with exact evidence.


---

# EXTENDED ENGINEERING FORENSICS — TAKEOVER EDITION II

> **Evidence convention:** `OBSERVED` means inspected repository content as of 2026-11-12; `REQUIREMENT` means desired behavior; `HYPOTHESIS` means an unverified explanation; `ACTION` means instructions to Codex; `GATE` means evidence required before rollout. These labels are deliberately not interchangeable. The sections below expand—not supersede—the first 47 folios. Where this extension identifies a contradiction, Codex must verify current code and record a dated decision rather than blindly implementing either interpretation.

## Folio 48 — Forensic source inventory and dependency map

**OBSERVED:** The following source files were additionally read from the `main` branch on 2026-11-12: `Scheduler.kt`, `ReAlertReceiver.kt`, `AlertStore.kt`, `Notifier.kt`, `EventLog.kt`, `RecoveryJobService.kt`, `Config.kt`, and `NotificationRemovalPolicy.kt`, all under `app/src/main/java/com/crewalert/missedalert/`. Inspect them again before changing code because commits may have advanced. Source snapshots establish how code is written, not necessarily how a physical Samsung device behaves.

| Component | Responsibility observed | Critical dependency | Failure to simulate | Verification obligation |
|---|---|---|---|---|
| `MissedAlertListener` | Notification capture, classification, sync after bind, source removal handling | Android listener grant, package list, `AlertStore` | Callback never arrives, reconnect replays item, key reused | State transition and device callback trace |
| `Config` | Policy-driven packages, headless launch flag, interval, max repeats | Android managed restrictions | Missing/invalid restrictions, enterprise push while active | Managed config change tests |
| `AlertStore` | Pending records, dedup/ACK markers, repeat count, due time | SharedPreferences, JSON | Torn write, invalid backup, keys reused, storage inaccessible | Crash/restart and corruption tests |
| `Scheduler` | Single rearming alarm and overdue recovery | AlarmManager, exact alarm access | Alarm canceled, permission revoked, clock changes | Instrumented timing traces |
| `ReAlertReceiver` | Fire reminder, increment repeat, reschedule | AlarmManager dispatch, notification post | Notification throws, receiver killed, duplicate broadcast | Fault injection with chain continuity |
| `Notifier` | Reminder channel and alternate notification IDs | System channels/volume/DND | Channel muted, notification suppressed, cooldown | Device sound proof |
| `RecoveryJobService` | Periodic health diagnosis and coarse recovery | JobScheduler | Job delayed, listener unbound, permissions missing | Job trace and honest degraded flags |
| `EventLog` | Daily diagnostics, retention, ZIP export | Filesystem, permissions | Disk full, write fail, rollover, large export | Integrity, retention, performance tests |
| `NotificationRemovalPolicy` | User versus app/system removal | Android removal reason | OEM reason changes, stale key | Reason table unit/device evidence |

**ACTION:** Produce a repository dependency graph from actual declarations and references, not this table alone. Include `BootstrapActivity`, `StatusActivity`, `ItDiagnosticsActivity`, `AckActivity`, `AckReceiver`, `BootReceiver`, XML resources, test sources, workflows, Gradle wrapper and any new files. Identify which methods run on the main thread, binder callback thread, broadcast receiver callback, job thread, or worker. Annotate lock acquisition and disk I/O. Map every source notification into its final acknowledgment operation and every reminder alarm into the next scheduling event. A missing edge is a candidate for the historical one-and-done defect.

## Folio 49 — Critical engineering findings, prioritized

**Finding F-001 — persisted monotonic deadline across reboot.** `Scheduler` persists `AlertStore.nextAt` as `SystemClock.elapsedRealtime()` milliseconds. Elapsed realtime resets at reboot, but SharedPreferences persists. If code interprets a prior-boot deadline as a current-boot deadline without validating a boot epoch, `isPending()`/recovery calculations can become misleading. Read `BootReceiver` and all recovery code to establish what clears or reconstructs `nextAt`, repeat counters, and pending records; add a reboot-specific regression. **This is a potential defect, not a proven failure.**

**Finding F-002 — exact alarm eligibility and product claims.** `Scheduler.scheduleNext()` invokes `setAlarmClock()` if `canScheduleExactAlarms()` returns true; otherwise `setAndAllowWhileIdle()`. The fallback is not a 60-second service-level guarantee. Exact-alarm restrictions and behavior depend on OS, app permissions and policy. A successful API call does not establish audible execution. Capture `mode`, permission state, scheduled timestamp, received timestamp, and late interval. **GATE:** If a test phone uses fallback, show DEGRADED TIMING prominently and do not claim guaranteed one-minute repeats.

**Finding F-003 — ACK policy drift.** `README.md` still states removal of source notification counts as acknowledgement. `MissedAlertListener.kt` and `NotificationRemovalPolicy.kt` make a stricter distinction: only designated user removal reasons mark the item acknowledged; source-app cancellations stay pending. `docs/XCOVER_KNOX_PILOT.md` supports the stricter behavior. Bring README, code comments, tests, and operator instructions into one explicit contract after verifying the actual workflow with SMS and MDT.

**Finding F-004 — logging limits and retention differ from earlier requirements.** `EventLog.kt` now declares 14-day retention, 50 MiB total and 3 MiB per daily file. The project minimum is at least seven days, so 14 days is a candidate improvement if test evidence confirms it. Its daily-size branch uses `daily.writeText(...)` followed by `daily.appendText(...)`, replacing the current day's earlier events when capacity is reached. That is a data-loss tradeoff, not append-only retention. Decide whether numbered daily segments or a bounded ring of segments is needed. Prove export of every retained segment, no abrupt memory spikes and clear overflow records.

**Finding F-005 — reminder published is not reminder heard.** `Notifier.show()` logs `reminder_posted` following `nm.notify()`, but audible playback can be suppressed by notification permissions, channel settings, DND, device sound routing, Samsung kiosk policies, repeated-notification cooldown or volume. Device acceptance must separate `post attempt`, `post accepted`, `notification visible`, `sound actually played`, `human acknowledgment`, and `repeat continued`. Never collapse these into a single PASS.

**Finding F-006 — possible persistence/serialization work on critical threads.** `AlertStore` uses synchronous SharedPreferences `commit()`; `EventLog` writes synchronously and may prune files during its first event each day. `MissedAlertListener.track()` and alarm callbacks can call these operations while synchronized on `AlertStore`. Measure blocking time, lock duration, cold-storage performance, and ANR risk. A safe design may keep critical state commits while moving non-critical diagnostics off the critical path using one bounded worker; do not trade state durability for speed casually.

**Finding F-007 — JSON pending records hold metadata only.** `AlertStore.add()` persists `pkg` and post time, not the `title` or `text` fields present on the in-memory `Item`. After process restart, the item is reconstituted without message contents, which is privacy-positive for storage and compatible with a generic reminder. Verify all `Notifier` and UI paths tolerate empty fields. Avoid logging source messages, phone numbers, contact names, dispatch details, patient information or notification extras.

**Finding F-008 — bounded ACK marker list may have linear lookup.** `AlertStore.isAcked()` loops over up to 4096 JSON entries. At high callback rates, repeated parsing and linear search may increase CPU. Benchmark realistic and worst-case rates; if needed, introduce bounded indexes without unnecessary third-party dependencies. Design expiry and safe replay semantics before pruning markers.

**Finding F-009 — alarm pending intent identity and stale callbacks.** `Scheduler` uses a fixed request code and `FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE`. A stale broadcast or duplicate callback could act against updated state. Add generation/sequence fencing to persistent scheduler metadata if existing code cannot distinguish valid from stale deliveries. Preserve single logical chain; prove duplicates cannot increment count twice or produce unwanted sound.

**Finding F-010 — failure isolation is partly implemented, but unverified.** `ReAlertReceiver` catches failures from notification display and reschedules in `finally`. `RecoveryJobService` attempts recovery in its own `finally`. These are positive patterns. Test deliberate failures in `Notifier.show()`, log writes, permission checks and store reads to confirm the actual chain continues or becomes observably blocked, without pretending every exception is recoverable.

**Finding F-011 — notification listener package matching needs live evidence.** `Config.SMS_PACKAGES` includes TraumaSoft variants and three common SMS providers; the listener also classifies missed calls from defined dialer/Telecom packages. Actual device notifications may differ by firmware, app version, channel or source. Capture observed package/channel/reason in privacy-preserving logs, and verify each vendor app posts notifications accessible to a listener. Do not use guessed package names as proof.

**Finding F-012 — alternate reminder IDs warrant audible behavior testing.** `Notifier` alternates IDs 4242/4243 and cancels the previous ID. This intends to prevent silent updates, but actual channel sound, cancellation interactions and OEM cooldown may defeat it. Test long sequences (at least ten cycles) and background/Doze transitions; ensure the alternation does not cause duplicate visible entries or spurious acknowledgment.

**Finding F-013 — the configured maximum could create intentional silence.** `max_repeats` defaults to zero (unlimited) but policy can impose a maximum. If a device policy unexpectedly sets a positive number, silence after a handful of reminders may be by design rather than alarm breakage. Log effective policy value on enrollment and self-test, include it in export, and distinguish `repeat_limit_reached` from scheduling failure.

**Finding F-014 — success depends on initial activation semantics.** The manifest includes a headless bootstrap launcher. Managed installation without first launch or service binding can leave the app unable to receive expected events under Android's stopped-package behavior. Verify Knox Auto-run and effective listener binding after device enroll/upgrade. Distinguish an app hidden from kiosk UI from a stopped or disallowed application.

**Finding F-015 — user-facing diagnostic accessibility remains a deployment concern.** IT diagnostics are not necessarily launchable by remote support just because an activity exists. Document an exact supported path that works with the organization’s Knox Remote Support mode and user restrictions. No adb dependence in routine production extraction.

For each finding create or link a GitHub issue or ledger item with owner, code anchor, reproduction, expected state, observed result, risk, countermeasure, acceptance evidence, and remediation SHA. Do not call a finding closed simply because a unit test was added.

## Folio 50 — Formal operational service-level objectives and error budgets

Product intent is repeat until acknowledged, not merely post notification once. The following **proposed acceptance targets** require owner sign-off and physical evidence; they are engineering targets, not currently achieved service levels.

| Measure | Proposed target | Source of truth | Important exclusions/notes |
|---|---|---|---|
| Enrollment readiness | All required effective grants and background conditions PASS before pilot | Device status export + Knox policy export | Assigned policy alone is not readiness |
| Source detection | Every eligible synthetic notification reliably tracked in controlled tests | Listener events against injected/post events | TraumaSoft live data requires legitimate test event |
| Due-time chain | At least ten uninterrupted repeat cycles on each qualifying matrix row | `alarm_scheduled`/`alarm_fired` event pairing | Audio must be measured separately |
| Audible confirmation | Each expected repeat independently observed on device | Controlled physical listening, audio instrumentation where authorized | System volume/channel/DND independently recorded |
| Acknowledgment stop | No reminders after a verified explicit ACK, subject to bounded in-flight callback | ACK event + subsequent observation window | Distinguish already-fired in-flight alarm |
| Idle and Doze | Correct degrade declaration; latency distribution recorded | Screen/power state + monotonic timestamps | Inexact fallback cannot promise 60 seconds |
| Process recovery | Pending items preserved on normal process death and valid rebind | Persistent state, listener/receiver logs | Android force-stop is not normal process death |
| Export integrity | ZIP opens, manifest present, all listed log files readable, no partial export | Automated ZIP readback + SHA-256 in test | Never imply cryptographic signature unless provided |
| Diagnostic footprint | Stays within documented configured storage cap | Filesystem measurements | Current code sets 50 MiB and 3 MiB/day |
| Memory overhead | Defined baseline versus steady/peak measurements, no unbounded growth | Device profiler/dumpsys on test devices | Numeric limit must be grounded in representative hardware |

Do not hide misses behind averages. Report count of eligible events, count tracked, number of repeats due, number fired, number posted, audible count, acknowledged count, false-positive count, and top failure reasons. Show 50th/95th/99th percentile lag only when sample size and timing are sufficient. Distinguish service availability from reliability of the external SMS/MDT source app.

## Folio 51 — State transition specification with audit IDs

Use an event-sourced mental model while preserving the current lightweight state store. `event_id` and `generation_id` may be logical concepts rather than newly persisted fields; introduce identifiers only when they solve proven ambiguity.

| Transition ID | Starting state | Trigger | Required behavior | Failure discipline |
|---|---|---|---|---|
| ST-001 | IDLE | Eligible source posted | Persist item, set next reminder, log package/channel | If persist fails, log critical error; do not falsely report scheduled |
| ST-002 | PENDING | Same notification re-posted | Update only permitted metadata; preserve initial due | No timer starvation |
| ST-003 | PENDING | Different source posted | Add item without delaying earliest pending | One shared alarm chain unless design proves otherwise |
| ST-004 | PENDING | Source app cancels event | Retain per current intended policy | Log reason and identity safely |
| ST-005 | PENDING | Android reports user clicked source | ACK exact generation, persist marker | Ignore stale callbacks |
| ST-006 | PENDING | Reminder content tapped | ACK selected generation then open source | Failure to launch source must not falsely imply delivery |
| ST-007 | PENDING | ACK ALL action | Atomically ACK every pending generation | Repeat chain canceled after successful commit |
| ST-008 | PENDING | Reminder swipe dismissal | Apply documented all-ACK behavior, if actually implemented | Never silently disagree with UI |
| ST-009 | PENDING | Alarm fires | Re-evaluate pending and permissions; post reminder; rearm | Logging failure cannot skip rearm |
| ST-010 | PENDING | Alarm is lost | Recovery job detects missing chain and rearms | Report gap and recovery latency |
| ST-011 | PENDING | Listener disconnected | Request rebind, preserve pending queue | Do not clear queue on disconnect |
| ST-012 | PENDING | Process killed | Resume from persisted state on next legitimate callback | No duplicate ack or loss |
| ST-013 | PENDING | Device reboot | Reconstruct coherent boot epoch/clock state | Explicitly define item retention versus reboot clearing |
| ST-014 | ANY | Source app upgrade | Re-evaluate package/channel identity | Do not assume package changed |
| ST-015 | ANY | App package replaced | Schedule recovery if allowed | No auto-start guarantee without policy verification |
| ST-016 | PENDING | Exact-alarm permission revoked | Enter degraded timing; use legal fallback | No claim of precise interval |
| ST-017 | ANY | Notification permission revoked | Record blocker and maintain recoverable pending state | Cannot force audible alert |
| ST-018 | PENDING | Logs inaccessible | Continue critical scheduling and safe state | Degraded observability flagged where possible |
| ST-019 | PENDING | Store corrupt | Restore only validated backup or explicit fault | Do not silently reinterpret as no alerts |
| ST-020 | PENDING | Max repeats reached | Enforce configured cap, surface intentional silence | Policy value visible to IT |
| ST-021 | PENDING | Wall clock jumps | Keep scheduling based on monotonic intervals, handle RTC display | Do not confuse wall time with elapsed due |
| ST-022 | PENDING | Daylight-saving transition | Preserve event chronology and date rollover | ZIP filenames and offset stamps unambiguous |
| ST-023 | PENDING | Device storage almost full | Bound diagnostics; preserve alarm chain where possible | Signal storage degraded |
| ST-024 | PENDING | User taps obsolete reminder | Validate generation and avoid clearing newer item | Pending queue not unexpectedly empty |

For each row Codex must add test fixture(s), log expected observations, and document whether behavior is VERIFIED UNIT, VERIFIED ANDROID, VERIFIED SAMSUNG, or UNVERIFIED. In particular, stale/duplicate alarm races require tests that expose ordering rather than merely testing individual methods once.

## Folio 52 — Notification identity, duplicate suppression and lifecycle model

Android notification callbacks can represent original posts, updates, group summaries, app-initiated replacements, and restored notifications. A key may remain stable when content changes; a post time may remain stable or change depending on operation. A reliable identity policy must be tested against actual SMS/MDT callback sequences, not guessed. Define the difference between a *notification generation* and a *real-world assignment*. The listener generally knows only the former. If multiple real-world messages are merged into one system notification, the app cannot infer message count from privacy-safe metadata unless the source exposes a supported signal.

Describe identity with `source_package`, `notification_key`, `source_post_time`, `generation`, `first_seen_elapsed`, `first_seen_wall`, `last_callback`, and `ack_status`. The present store does not include all these fields, so map current values and only add the minimum useful fields. Avoid message body hashes as a workaround: they can still disclose personal data through dictionary or frequency inference. Test key reuse with old removal arriving after a new post, duplicates with exact same key/time, app update replacing a notification, source app clearing a notification without crew action, phone reboot and listener sync enumerating existing notifications, and a call provider changing its channel ID. Distinguish false-positive duplicate suppression from false-negative disappearance.

The baseline listener skips `FLAG_ONGOING_EVENT` and `FLAG_GROUP_SUMMARY`; verify that actual TraumaSoft alerts are not excluded by these flags. Confirm `Notification.CATEGORY_CALL` is not enough to treat an incoming call as missed; current code additionally checks missed-like channel/title/text. Test non-English locale, text capitalization, notification visibility restrictions and OEM channel naming. Never log raw title/text just to diagnose misclassification; for controlled laboratory fixtures, use fabricated content and a privacy-safe classification reason code.

## Folio 53 — Acknowledgment UX and human factors contract

In an EMS environment, “acknowledged” should mean the crew or approved workflow deliberately accepted responsibility for reviewing an alert. A source app deleting a notification can be a programmatic cleanup, not proof of human review. Conversely, clicking a notification may open a screen but not guarantee dispatch was read. The software should make no stronger claim than the signal observed. Distinguish `REMINDER_DISMISSED`, `REMINDER_OPENED`, `SOURCE_NOTIFICATION_CLICKED`, and `TASK_HANDLED_IN_SOURCE` as different semantics. The fourth cannot be inferred without vendor integration.

Write a one-page operator policy for all reminder actions with precise statements: what tap does, what ACK ALL does, what swipe does, what source-notification dismissal does, what happens when source app is unavailable, and whether any event may create re-alerting after explicit ACK. Capture all actions in a table showing whether one item or all items are affected. Preserve these semantics across app upgrades and managed configuration changes. Do not add a crew-facing status screen just to make tests easier. Pilot with IT staff on non-clinical devices using fabricated messages; collect feedback about accidental ACK and whether opening the source actually leads to correct crew work. Add safeguards only when they do not undermine fast emergency workflows.

## Folio 54 — Alarm and scheduling proof obligations

The reminder path is a chain, not one alarm: source capture → durable pending store → due time persisted → AlarmManager receives request → app receiver invoked → reminder channel post attempted → callback returns → next due scheduled. Failure anywhere can cause the one-time alert symptom. Instrument chain correlation without raw message identifiers. For each `alarm_scheduled`, there must be either a corresponding `alarm_fired`, a documented cancellation due to ACK/configuration, or a detectable missing alarm/permission fault. Missing pairs require an exception entry in the audit report. Multiple scheduling requests should not unexpectedly postpone the first item. Recovery should be idempotent if listener and periodic job race.

Investigate exact-alarm APIs with current Android docs, not inherited README claims. On a device with exact alarms available, log `setAlarmClock()` selected and whether the system accepts scheduling. On fallback, record `setAndAllowWhileIdle()` and why exact was unavailable. Capture `elapsedRealtime`, `uptimeMillis`, wall time with zone offset, reboot/boot epoch signal, due time, late milliseconds, SDK, device build fingerprint (privacy-reviewed) and power state. Diagnose Android 15+ notification cooldown separately from scheduler accuracy. Avoid loops, perpetual wakelocks, minute-by-minute polling jobs, or unnecessary foreground services; they increase battery and may violate platform behavior without improving outcomes.

## Folio 55 — Persistence and crash-consistency specification

The existing store uses `SharedPreferences` with synchronous commits and JSON strings (`items`, `items_backup`, `acked`, `acked_backup`, `repeats`, `nextAt`). A backup that simply mirrors the latest write may not protect against data corruption or partially consistent logical state; test exactly what it protects and what it does not. Simulate a crash between writing pending state and alarm scheduling, and between a reminder firing and the next alarm registration. On recovery, distinguish empty queue, damaged storage, unreadable storage, and queue with overdue alarm. Never take “JSON parse failed” to mean “zero alerts.”

Define invariants around ACK: once an ACK transaction succeeds, the corresponding generation is both absent from pending and present in dedup markers; if commit fails, neither state should be reported as successful. Consider schema versioning and migrations for changes to item fields. Migration must preserve legitimate unacknowledged alerts and must not reanimate a long-cleared alert. Keep backup/encryption policies aligned with `allowBackup=false`; no private notification content in diagnostics. If introducing a transaction journal, justify additional storage and CPU cost against reliability evidence. Hard requirement: worst-case corruption must be observable by IT, with safe recovery or a clearly blocked state rather than silent success.

## Folio 56 — Multi-source race catalog

Test real ordering permutations instead of ideal callback order. Two SMS notifications may arrive within one listener dispatch; an MDT alert may arrive while `ReAlertReceiver` is posting a reminder; a source app may cancel while a reminder tap begins; `ACK ALL` may run while another notification posts; the listener may disconnect while the recovery job requests rebind; the exact-alarm permission might change during `scheduleNext`; `Config` might change the interval while pending. Log the chosen serialization boundary, and prove state transformations do not deadlock, lose items, defer first due time, or leave a pending queue without an alarm.

The current code synchronizes portions on `AlertStore`, and some store methods are themselves synchronized. Java monitors are reentrant on the same thread, but correctness across components still depends on lock ordering and callbacks. Examine whether `EventLog.write`, notification dispatch, disk I/O and `JobScheduler` operations occur while holding locks. Instrument lock times under slow storage and simultaneous calls. Prefer one small deterministic state-machine coordinator where justified, not many competing timers. Show before/after cardinality for every shared-state mutation and include a liveness assertion: if pending count stays positive and permissions remain available, future recovery or reminder callback must remain possible.

## Folio 57 — Deployment chronology as a verified runbook

**Pre-enrollment:** Record precise model, SKU, OS/build, Knox Manage policy version, KSP plugin version, device ownership status, source app versions, default SMS app, kiosk rules, notification-bar policy, DND configuration, battery policy, signed APK certificate fingerprint, app version code and source commit SHA. No patient or crew identifiers in shared dossier. Confirm test device is not relied on for live clinical communications.

**Stage A: install.** Sign the exact candidate APK using the authorized corporate keystore, ensure package and signing certificate match in-place upgrade requirements, upload to Knox, assign to a pilot device group, and verify actual installation on device rather than interpreting assignment as success.

**Stage B: initial activation.** Verify Knox Auto-run after install/update and run-once controls. Confirm `BootstrapActivity` can activate invisibly under kiosk without a visible persistent window. Capture `bootstrap`, listener binding and policy status events. If auto-launch is blocked, record actual Knox policy error, not a generic “app broken.”

**Stage C: permissions.** Verify effective `POST_NOTIFICATIONS` where applicable, Android notifications enabled, notification-listener special access, alarm/exact schedule capability, battery optimization exemption, force-stop protection if actually supported, and notification settings for SMS and TraumaSoft. Check the managed app restrictions actually read by `Config`. Note that battery exemption cannot make a manually force-stopped package magically restart; verify the enterprise restriction’s real effect instead of assuming it.

**Stage D: kiosk.** Hide the app from the crew-facing kiosk list while allowing its required process/background behavior. Check whether notification bar restrictions suppress sound and whether reminders appear or can be ACKed in the intended mode. Distinguish `Allowlisted Apps Settings / Apps outside kiosk` behavior from actual installed packages. Use controlled remote support to access IT diagnostics and export files; do not use adb as a required production step.

**Stage E: acceptance.** For SMS, MDT and missed calls, test immediate delivery, ten repeated reminders, intentional ACK, silent source cancellation, screen off, 30-minute Doze, reboot, package upgrade, permission withdrawal/restoration and simultaneous sources. Log what passed, failed and was not tested. Do not deploy to active fleet before sign-off.

**Stage F: rollout.** Roll out in waves with rollback APK/policy ready, monitoring daily exported health evidence from representative devices, and an issue escalation path. Preserve signatures and log access methods. All stages require evidence and operator initials/date in an IT-controlled checklist.

## Folio 58 — Knox failure-mode decision tree

Start from observable symptoms rather than guessing. If **no `listener_connected`** exists after enrollment, check installed app state, initial launch, listener grant, binding and kiosk/managed policy. If listener connects but **no `alert_tracked`**, check actual source app package, notification enablement, eligibility flags and whether the source posts Android notifications. If tracked but **no `alarm_scheduled`**, audit store write, effective max repeats, schedule exceptions and exact-alarm access. If scheduled but **no `alarm_fired`**, inspect Doze, reboot, alarm manager state and background restrictions. If fired but **no `reminder_posted`**, inspect channel, notification permission and exceptions. If posted but **no audible tone**, examine channel sound, DND, Android notification cooldown, volume/routing, silent system setting and Knox kiosk notification policy. If audio occurs once then ceases, inspect `max_repeats`, ACK reasons, new due timestamp, repeat counter, cancellation, scheduling fallback, and `nextAt` persistence. If reminders continue after ACK, inspect stale PendingIntent, mismatched key/generation, bulk ACK atomicity and duplicate source reposts. If no logs export, inspect IT activity access, permission, filesystem and ZIP export logic.

For every branch of this decision tree Codex should provide one local reproducible scenario, a minimal sequence of log events showing healthy operation, a failure signature, the relevant code anchor and a recovery action that does not expose private messages. Capture ambiguity: absence of a log line may mean the event did not occur **or** logging failed. Cross-check with device system logs or an independent test observer where possible.

## Folio 59 — Observability event contract version 2

Diagnostics must distinguish state changes, attempts, successes and observations. Define an event taxonomy with stable `event_type`, schema version, `source_kind`, `source_package` where safe, `notification_generation_id` as a nonreversible local opaque surrogate, `scheduler_generation`, `pending_count`, `wall_timestamp_with_offset`, `elapsed_ms`, `boot_id` or equivalent reboot cohort, `device_model`, `api_level`, `app_version`, `policy_revision`, `result`, `failure_reason`, and `correlation_id`. The existing logger's human-readable line format can be kept for lightweight compatibility while adding consistent keys. Do not overengineer a database or network telemetry pipeline without justification.

Required event families: lifecycle, listener, source capture, classification, pending state mutation, alarm scheduling, alarm receive, notification post, acknowledgment, watchdog, permissions, channel/audio environment, policy update, export, storage health, regression self-test and explicit operational blocker. Health events should differentiate PASS, FAIL, INCONCLUSIVE and NOT RUN. Every entry must be safe to export to support staff: no phone numbers, SMS bodies, message titles, names, dispatch addresses, patient information, authentication secrets, IMEI or arbitrary notification extras. Avoid including full Android notification keys if they embed device/user-related strings; hash or locally alias them only if necessary and privacy-reviewed.

For replays, construct two views: (1) chronological raw event list; (2) reconstructed timeline by correlation/generation, with missing edges. Export both only if storage/computation costs remain bounded. End users must be able to answer: Did the app detect the notification? Was it queued? Did the first repeat fire? Which future alarm was scheduled? Which permission or channel blocked sound? Did someone deliberately acknowledge it? Was this test physical or synthetic?

## Folio 60 — Diagnostic log retention and export hardening

Current `EventLog.kt` keeps 14 calendar days including today, enforces a nominal 50 MiB total cap and 3 MiB per daily file, and streams ZIP with an 8192-byte copy buffer. Its daily-size handling overwrites the existing day's file with a rotation marker, potentially discarding earlier same-day history. Investigate numbered segments such as `events-YYYY-MM-DD-0001.log`, preserving older segments until the retention policy prunes them, or a documented bounded daily ring strategy. Code changes must include backward-compatible parsing/export and tests for day boundary, out-of-space, file rename failure, ZIP interruption, partial temp file cleanup, concurrent writing/export, invalid characters, very long event fields and user changing device clock. Bound number of files and memory consumption.

Minimum ZIP content: `device-status.txt`, `manifest.json` with schema and collection timestamp, retention policy, app/OS/Knox characteristics, redacted managed config summary, count/list of included files, per-file size, per-file checksum, `export-complete` marker or successful atomic publication semantics, and daily logs. If checksums are produced, calculate using bounded streaming reads and verify in CI; do not imply any signed attestation. Readback test must open the ZIP, verify every listed entry, compare checksums and reject truncation, duplicate names, path traversal names and partial downloads. If the export is retried, it must not corrupt the previous valid file.

Operators need literal taps/steps that work under remote support, a file location that the enterprise remote support agent can access and a clear result indicator. An export that exists only inside app-private inaccessible storage does not satisfy the operating model. Document what remote support can and cannot do on each Knox variant with evidence from a pilot, rather than assuming universal file-picker access.

## Folio 61 — Autonomous testing architecture and truthful boundaries

Organize tests by determinism and evidence strength. **Layer 1: pure model tests** for state transitions, identity/generation, ordering, time math and reason classification. **Layer 2: Robolectric** for SharedPreferences, AlarmManager call selection where supported, intents and channel construction. **Layer 3: Android emulator instrumentation** for real framework binding/permission behavior where feasible; emulator success does not imply Samsung success. **Layer 4: managed physical Samsung lab** for Knox policies, screen-off, Doze, channel sound, vibration, actual SMS/MDT notifications and OEM process management. **Layer 5: controlled operational pilot** on noncritical devices with support staff acceptance and incident procedures. No layer may be relabeled as a stronger one.

Use CI to compile and run deterministic tests on each change, archive results while respecting GitHub Actions artifact quota, and fail closed if mandatory security/race/regression suites fail. If artifact quota is unavailable, publish concise job summaries and preserve necessary lightweight evidence, while reporting the constraint. For device-only tests that cannot be automated without hardware, generate a reproducible procedure and mark `NOT RUN (physical hardware unavailable)` rather than passing. Build system does not need paid remote APIs or Docker. Automated tests should use fake notifications and synthetic metadata; never contact real patient/dispatch systems without authorization.

## Folio 62 — Controlled notification test harness design

Build an optional test-only harness that posts fabricated notifications from a separate app/package under the same Android system, because an application's own notifications are intentionally ignored by the listener. Supply a permission-safe fixture package with named channels, category types, flags, group summaries, stable keys, re-posts and cancellation sequences. Add a second harness that programmatically sends ACK actions, changes test config and takes device snapshots through permitted test interfaces. Ensure the harness cannot ship enabled in a clinical release or generate alarms on live crew phones without an explicit IT-controlled test mode. No hard-coded patient or phone information.

Define standard fixture names: `SMS_SINGLE`, `SMS_GROUP_UPDATE`, `MDT_SINGLE`, `MDT_APP_CANCEL`, `MISSED_CALL_CANONICAL`, `INCOMING_CALL_IGNORE`, `ONGOING_IGNORE`, `GROUP_SUMMARY_IGNORE`, `DUPLICATE_POST`, `POSTTIME_NEW_GENERATION`, `STALE_REMOVAL`, `TWO_SOURCE_SIMULTANEOUS`, `REBOOT_PENDING`, `CHANNEL_MUTED`, `SCHEDULE_PERMISSION_REVOKED`. For each fixture record expected callback order, expected store cardinality, next due interval, first reminder sequence number, ACK semantics and export evidence. The harness must not claim to test actual TraumaSoft unless a real authorized TraumaSoft notification is generated independently.

## Folio 63 — Fault injection catalog and explicit recovery expectations

Create deterministic injection hooks only in debug/test builds where possible; keep production behavior clean. Simulate `SharedPreferences.commit()` returning false, malformed primary JSON with valid backup, corrupt primary and backup, `AlarmManager` throwing `SecurityException`, fallback throwing a generic exception, `NotificationManager.notify()` throwing, channel disabled, log writer throwing, ZIP stream failing mid-write, JobScheduler rejecting a job, listener returning null active list, duplicate rebind, simultaneous ACK, process kill, app upgrade, clock reset and disk exhaustion. Observe whether critical state remains coherent, whether alarm recovery is requested and whether logs truthfully record failure.

Never suppress test failures with broad `catch (Exception)` in test code. Production code may isolate noncritical exceptions when justified, but each catch should preserve a useful typed diagnostic and a known degradation state. At minimum, verify that a failed log write cannot abort pending mutation, a failed notification post does not silently break future scheduling, an unreadable store does not become an empty store, and a failed alarm request cannot set a falsely healthy due state. Verify exact ACK transaction outcomes when persistent commit fails. Only report recovery when the subsequent state/event confirms it.

## Folio 64 — Memory, power and ANR engineering discipline

The product should run silently on low-cost Samsung Android hardware and remain negligible beside normal crew applications. Measure **baseline process PSS**, **idle 24-hour PSS**, **peak during 50 simultaneous simulated notifications**, **peak during ZIP export**, **CPU time/hour**, **wakeups/hour**, **alarms/hour**, **disk writes/hour**, **bytes of retained logs**, **handler callback duration**, and **battery drain under comparable controls**. Never invent a numeric threshold; choose acceptance bands after baseline tests with appropriate OS/model repeatability and safety margin. Current `gradle.properties` sets a Gradle daemon heap limit of 2 GiB for builds; it is not the Android app's runtime heap budget.

Potential performance risks: repeated JSON parse and rewrite for every callback; array scan of 4096 ACK markers; synchronous disk commit; synchronous log append; daily prune on first log entry; full-file tail reads; ZIP export contention with active writes; package-manager label queries during reminder creation; high-frequency `setAlarmClock()` repeating for days. Benchmark rather than preemptively rewrite. Good targets include allocation-light callbacks, no large external runtime libraries, bounded state/log queues, reused immutable configuration snapshots when appropriate, no busy waiting, and no indefinite wake locks. If introducing a background executor, define backpressure and shutdown/recovery behavior.

## Folio 65 — Security and privacy red-team exercise

Assume a malicious app posts malformed notifications, a device administrator misconfigures permissions, a user can tap or dismiss a reminder in unanticipated order, a storage file is corrupt, and a diagnostic export is forwarded to an outside support person. Verify all exported metadata is sanitized; package names are generally less sensitive but are still enterprise context. The current logger truncates field values to 80 characters and replaces whitespace, which is **not** a sufficient general-purpose data classification policy. Audit every call site for untrusted strings, newline characters, log forging, exceptions that include secret text and activity intents containing identifiers.

Review `ItDiagnosticsActivity`'s `android.permission.DUMP` exposure under Android signature privileges; verify it cannot be launched by ordinary third-party apps. Confirm exported activities/receivers and their intent validation. Check PendingIntent mutability and flags, component target explicitness, notification intent lifetime, old intents after ACK, and malicious package impersonation via managed extra_packages. Check local state/ZIP files against backup, world readability and external file exposure. Create a threat register with attack path, affected asset, risk, mitigation, tests, residual risk and security sign-off. No private notification content should appear in GitHub issues, CI artifacts or Codex prompts.

## Folio 66 — Audit of compatibility claims and current vendor authority

The README contains claims about Android 13–17, Android 15+ cooldown, Knox KSP permission grants, alarm clock behavior and kiosk notification-bar restrictions. Codex must revalidate each against current official Android and Samsung Knox docs at takeover, because platform and OEM behavior can evolve. Record the document URL, revision/date, exact applicable API/device range, assertion supported and test device evidence. Do not substitute anecdotal blog posts for permission semantics. Particularly verify exact alarm access restrictions for target SDK 36, notification listener privilege via Knox, battery optimization exemption, KSP policy keys, Samsung notification bar effects, and managed restrictions behavior. Treat an official document as policy description, and a physical test as proof of actual firmware implementation.

## Folio 67 — CI gate blueprint and artifact hygiene

Minimum build pipeline: clean checkout pinned to commit SHA; supported JDK/Android SDK; dependency verification; Gradle wrapper validation; static analysis; unit/Robolectric tests; debug APK assembly; manifest/package/version assertions; lint; optional security checks; report generation; and unsigned/signed artifact handling with secrets protection. Run separate jobs when independent, but avoid redundant full Gradle downloads or repeated builds that exhaust quota. Use Gradle cache and conservative concurrency to minimize billed minutes, without sacrificing deterministic results.

A failed job must identify exact command, exit code, test report and commit. Never label a job PASS because it is green if required tests are skipped. Signing should be confined to release workflow or authorized secrets; never print keystore password, export private keys or write them to workspace caches. Keep release provenance: input SHA, version code/name, signing certificate digest, generated artifact SHA-256, build environment and CI URL. Manage artifacts by retention policy and delete obsolete large build artifacts only under approved repo retention settings. Publish small human-readable summaries in addition to machine-readable reports.

## Folio 68 — Release, update and rollback safety case

A release is not “an APK compiled.” It is a frozen source revision plus code/manifest tests, build identity, signing validation, Knox compatibility validation, physical behavior evidence, install/update/rollback plan and explicit acceptance. Define release types: development, IT pilot, expanded pilot and production. A pilot build must be conspicuously labeled in version metadata and distribution group. Avoid distributing multiple indistinguishable APKs. Future versions must increment version code consistently and remain upgrade-compatible with the existing signing certificate unless IT explicitly plans a reinstall.

Rollback requires knowing whether Android allows downgrading without data loss for that package and deployment mode; do not assume it. Preferred mitigation can be a re-release of prior known-good code with higher version code signed identically. Capture policy rollback separately from APK rollback. After rollback verify listener grants, alarm scheduling, pending state/ACK migration behavior, log export and source app notifications. If pending state is migrated incompatibly, design a reversible migration or fail safely with explicit IT remediation. Never change the signing identity to make Knox accept an APK without validating upgrade consequences.

## Folio 69 — Production incident runbooks

**Incident A: No reminders across fleet.** Declare scope/time, preserve device and Knox policy evidence, inspect recent app or KSP policy deployment, verify source notifications enabled, listener and reminder permission grants, channel settings, and any fleet-wide DND/notification-bar changes. Avoid mass reinstall before collecting evidence. Roll back only the known offending policy or release.

**Incident B: One phone stopped after reboot.** Compare device boot time, app first launch, listener binding, persisted due state, recovery job state, battery policy and actual source app notifications. Export logs, execute isolated synthetic test and determine whether app was force-stopped or access was revoked. Label unknown cause as unknown.

**Incident C: Repeats visually but silently.** Do not change scheduling code first. Inspect channel and posting enabled, device ringer/notification settings, DND, Bluetooth route, separate app sound, notification cooldown and kiosk notification policy. Coordinate with Volume Guard checks. Measure physical sound with repeat evidence.

**Incident D: Repeats never stop.** Determine whether source app silently removed notification or whether ACK callback/marker persist failed. Confirm policy contract before changing behavior; do not auto-ACK source-app cancellations as a quick fix without safety review.

**Incident E: Excessive battery or storage.** Measure actual wakeups, pending alerts, repeats, loop frequency, log volume and export contention. Retain relevant evidence before cleanup. Check whether a low repeat interval or a long-unacknowledged alert is creating expected sustained notifications; avoid silently reducing reliability without authorization.

**Incident F: Export corrupted or missing.** Recreate with a test ZIP, inspect temp/final publication logic, storage space and concurrency, preserve old logs, and verify with an independent unzip tool. If export cannot succeed, label observability blocked and prevent unsupported pilot acceptance claims.

Every incident file must contain incident ID, start/end, affected models/app versions/policies, symptoms, impact statement, safety workaround approved by operations, log attachments, root cause confidence, corrective change, regression results and closure authority.

## Folio 70 — Operator/IT user manual expansion requirements

Create a separate plain-language manual that answers: Why is this installed? What do staff see? Does staff have to open the app? What does the reminder mean? How is it acknowledged? What happens when a source app clears its notification? Why can a reminder be quiet even at maximum volume? How does IT check device health? How does IT remotely export the last 14 days of logs? How is a test alert issued without creating a false real dispatch? How does an IT admin update policy, APK and package watchlist? What does every red/amber/green field actually mean? What should IT do after each specific failure code? How do we roll back safely?
Include OS-specific screenshots only if captured from a validated device; do not invent steps from memory. Supply a brief crew FAQ, one-page IT installation checklist, five-minute basic triage, detailed engineering diagnostics and emergency escalation. Explicitly state that the app is an additional reminder mechanism and is not a replacement for primary dispatch communication, standard EMS procedures or clinical response protocols. The manual must remain synchronized with actual action labels and Knox policy naming in deployed version.

## Folio 71 — Cross-project boundary: MMT Volume Guard

The companion project MMT Volume Guard attempts to keep alert volume levels appropriate on managed Samsung devices. Missed Alert should own detection/pending/ACK/repeat semantics; Volume Guard should own permitted volume enforcement. Keep codebases independent and avoid undocumented private intents, shared preferences or service coupling. An installed Volume Guard is not proof that DND is disabled, the reminder channel is audible, the device speaker is selected or kiosk notification rules allow sound. Validate both versions on the same pilot phone, record audio before/after, and test under disallowed notification bar settings.

Define an integration fixture where Volume Guard is absent, present and misconfigured. Verify Missed Alert truthfully reports sound blockers without attempting to take over Volume Guard's policies. Do not add a direct dependency that makes the reminder app crash when Volume Guard is unavailable. Distinguish “volume at 100%,” “channel has sound URI,” “system allowed notification,” and “sound physically emitted” in test reporting.

## Folio 72 — TraumaSoft-specific test campaign

Use the actual TraumaSoft MDT application on a nonproduction managed phone with authorized test dispatch workflow. Document application package, version, channel, notification category, flags, post/update/removal callback order, grouping behavior, priority level, source-app visibility in kiosk and whether notification is canceled when the crew opens the app or marks an assignment as handled. Avoid copying dispatch details into logs. If no Android status-bar notification is posted, state that the present listener architecture cannot see it. Investigate official vendor options only after collecting that evidence.

Test at least: new assignment with app foreground; background; kiosk foreground on different app; screen locked; app process restarted; assignment updated; assignment canceled by dispatch; two assignments arriving rapidly; notification tapped; task handled within MDT; source app notification silently removed; app upgraded; network outage/reconnect (as source-app scenario). For each, produce a behavior map with observed notification and reminder transitions. Mark whether ACK is a reminder acknowledgment or an actual MDT workflow completion; do not conflate them.

## Folio 73 — Google/Samsung Messages-specific test campaign

Default SMS handling differs by Android version and provider. Verify that Google Messages actually posts a notification for each test message, that group conversations and notification bundling behave as expected, and that tapping an SMS notification generates a user-related removal reason distinct from app background cleanup. Test initial unopened message, second message in same thread, high-volume burst, message marked read from another device, notification cleared via shade, SMS app killed/restarted, default SMS app switched by policy, RCS notification under Google Messages if used, silent conversation channel, and device-wide notification block. Keep test data fictitious. Avoid reading SMS inbox or requesting invasive SMS permissions; the listener's purpose is visible Android notifications.

## Folio 74 — Missed-call-specific test campaign

Missed-call classification is heuristic: packages in `CALL_PACKAGES` and/or `CATEGORY_CALL`, exclusions for ongoing/foreground notifications, and channel/title/text containing “missed”. Test on each representative phone and locale using authorized test calls, including incoming ringing call, answered call, declined call, actual missed call, repeated missed calls, voicemail, call waiting, call forwarded, carrier dialer overlay, voicemail notification, Bluetooth call handling and dual-SIM where present. Never count an ongoing call as missed. Record actual package and channel while redacting numbers and contact names. If the heuristic fails across locales or OEM versions, build a safer classifier based on observed immutable metadata or declare unsupported profiles rather than broadly classifying all calls as missed.

## Folio 75 — First 24 hours after Codex takeover

**Hour block 0–1 (sequence, not promised elapsed duration):** Get current repository head, branch state, open issues/PRs, workflows and test sources; read this dossier; produce inventory with line anchors. Do not edit until conflicts and unknowns are clear. **Block 2:** Compile and run available tests; record actual results and environmental blockers. **Block 3:** Walk the original one-alert/no-repeat defect through callback→store→schedule→fire→post→rearm with evidence. **Block 4:** Implement the highest-confidence minimal correction with targeted tests. **Block 5:** Run baseline tests and neighboring regressions. **Block 6:** Inspect build artifact, manifest and release settings; do not deploy to a clinical device. **Block 7:** Update dossier, README, operator manual, feature status, ledger and precise resume point. **Block 8:** Queue independent next work: ACK correctness, diagnostic export, Knox setup, physical pilot requirements. The blocks define execution order and should never be interpreted as guaranteed completion times.

Write one checkpoint per completed unit containing HEAD before/after, exact commands, outcomes and unverified hardware gaps. If Codex is interrupted, a new session must be able to read just `docs/MASTER_ENGINEERING_DOSSIER.md`, `docs/ENGINEERING_LEDGER.md` and `docs/NEXT_SESSION.md` plus the repository to resume without asking the user to re-explain the mission.

## Folio 76 — Codex file ownership, subagents and conflict avoidance

When parallel execution is available, split independent tasks into architecture/race audit, test inventory, Knox official-doc verification, diagnostics/privacy review, user manual alignment and CI/build review. Only one agent should modify any single code file at a time. A coordinator owns integration, semantic decisions, signing/release impacts and the cumulative dossier. Do not parallel-edit state-machine core, shared Gradle files or the same documentation ledger without explicit synchronization. Use temporary worktrees if allowed for isolation, and integrate into `main` according to protections after independent review. Avoid creating permanent branch sprawl, duplicated implementations or contradictory documentation.

A reviewer must compare each patch against requirements and test evidence, not merely its author's summary. A reviewer should ask what was *removed*, what invariant changed, how many callbacks can be in flight, what happens if process dies at the worst line, and how failure manifests on a real phone. Functional changes cannot be approved solely because they are clean-looking or memory-efficient; both correctness and performance require measurement.

## Folio 77 — Product completeness matrix and ownership rules

Track each requirement with: unique ID, user need, implementation location, dependency, candidate test, measured evidence, risk, operator impact, status, blocker and next action. Starting columns should include `REQ-DET` notification capture, `REQ-ACK` explicit acknowledgment, `REQ-REP` repeating alarms, `REQ-AUD` audible reminders, `REQ-LIFE` lifecycle/reboot, `REQ-KNOX` zero-touch policy, `REQ-LOG` daily logs, `REQ-EXP` remote ZIP, `REQ-PERF` low memory/power, `REQ-SEC` export privacy, `REQ-CI` autonomous tests, `REQ-REL` release provenance and `REQ-MAN` IT manual. Never mark a requirement DONE based on implementation existing; use `CODE PRESENT`, `UNIT VERIFIED`, `ANDROID VERIFIED`, `SAMSUNG VERIFIED`, `PILOT VERIFIED` and `GATED/BLOCKED` distinctions. Derive a real product readiness percentage only if the owner approves weighting and there is a denominator of objective requirements; otherwise use a transparent checklist.

## Folio 78 — Engineering decision record format

Every consequential tradeoff gets an ADR with ID, date, author, problem, constraints, options considered, selected option, rationale, evidence, impact, rollback and revisit trigger. Required initial ADRs: explicit ACK interpretation; state persistence across reboot; exact versus inexact fallback handling; one shared alarm versus per-alert alarms; log segmentation at daily cap; channel version and notification-ID alternation; background execution without a permanent foreground service; how IT-only diagnostics launch under kiosk; maximum pending record retention; and whether missed calls should be within first production wave. Link ADRs to implementation SHA and test cases. When later evidence falsifies an ADR, supersede it with a new record; do not silently overwrite history.

## Folio 79 — Requirements traceability: backward and forward

For every source file and test, identify which requirement it serves; for every requirement, identify code and tests. A file with no requirement may be dead weight or a hidden dependency, and a requirement with no tests is unverified. Build a traceability table keyed to IDs, not prose-only claims. Include expected exception behavior and log event for each critical requirement. Spot-check that marketing phrases such as “zero touch,” “60-second reminder,” “Doze proof,” “14 days logs,” “automatic recovery,” and “self-test” have precise definitions, limitations and evidence. Unqualified promotional wording is prohibited in operator docs until supported by physical testing.

## Folio 80 — Proof-oriented regression test templates

For each test implement a fixture named with a stable ID, a clear arrange/act/assert, independent setup/cleanup and a deterministic clock where possible. Add artifacts only when failure diagnosis requires them. The example below is a **specification**, not runnable code:

```
CASE ID: REG-ALARM-OVERDUE-001
Arrange: one durable pending generation, valid permission, persisted due time 90 s overdue.
Act: trigger recovery twice concurrently; deliver first valid alarm once.
Assert: exactly one logical next alarm is pending; one repeat is committed;
        late_ms is nonnegative; pending item remains; no unbounded work occurs.
Negative: inject channel post failure; chain must still rearm or report blocked.
Evidence: source SHA, test command, result, selected event trace, test device class.
```

Do not reuse a single green unit test as proof for many hardware behaviors. When a physical scenario is untestable in the current environment, create the exact lab protocol and store the pending acceptance row with NOT RUN status.

## Folio 81 — Field acceptance sampling and evidence handling

Use a representative device matrix rather than testing a single convenient phone. At minimum include A14/A15/A16/A17 and XCover Pro when they remain in deployment scope; group SKUs only when firmware and effective policy equivalence is shown. Record OS API, One UI version, app build, source app versions and policy revision. Choose repeated trials across screen-on/off, charging/not charging, network-connected/temporarily disconnected, source app foreground/background, and after reboot. Export reports with anonymous device fixture IDs. Test multiple actual notifications but avoid clinical traffic. If an OEM-specific failure appears, isolate whether it is app logic, source app, OS scheduling, notifications, device mode or Knox policy.

Lab technicians should record predicted reminder due times before executing, observed actual reminder sound times, device and independent clock synchronization, sound routing, operator acknowledgment times, notification disappearance and diagnostics. Audio observations can be human-confirmed with a second observer or measured using controlled equipment, but never inferred from `notify()` success. Use a consistent evidence naming convention and a manifest mapping experiment ID → device → build SHA → policy SHA → result → evidence location. Retain no sensitive patient/dispatch content.

## Folio 82 — Anti-hallucination and status-report contract for Codex

Every claim must begin with one of `IMPLEMENTED (not tested)`, `TESTED PASS`, `TESTED FAIL`, `BLOCKED`, `NOT RUN`, `NEEDS DEVICE EVIDENCE` or `DECISION REQUIRED`. Include tests commands, SHA and source artifact for PASS. Never say “ready for production,” “100% verified,” “always works,” “guaranteed in Doze,” “runs even force-stopped,” or “all Knox devices supported” without complete matching evidence. If source code or secrets cannot be accessed, report the exact limitation and proceed with unblocked work. If Codex cannot remain running, stop at a complete checkpoint and leave `docs/NEXT_SESSION.md` with one copy-paste resumption instruction. Do not imply that merely adding an instruction makes autonomous unlimited execution possible.

## Folio 83 — Document control and consistency validation

At each meaningful milestone update: `docs/MASTER_ENGINEERING_DOSSIER.md` (this document); `docs/ENGINEERING_LEDGER.md` (append-only change and evidence journal); `docs/NEXT_SESSION.md` (exact resumption instructions); `docs/FEATURE_STATUS.md` (current requirement states); `docs/ROADMAP.md` (priorities and dependencies); `docs/USER_MANUAL.md` (confirmed operator procedures); `docs/KNOX_DEPLOYMENT.md` (policy and fleet settings); `docs/TEST_MATRIX.md` (test IDs and statuses); `docs/RELEASES.md` (build provenance). Existing files may differ; merge with them rather than create duplicate competing authorities. Add a lightweight link checker and consistency test ensuring documented package/version/source values match Gradle and manifest, and verify the README links to the dossier, operator manual and latest known-gaps page. Preserve a dated snapshot of major decisions.

## Folio 84 — Codex emergency recovery and session handback template

At every interruption emit the following populated block, never leave placeholders in a claimed completed handoff:

```
PROJECT: DevTunaSoft/missed-alert
AUTHORITATIVE BRANCH / HEAD SHA:
WORKTREE DIRTY OR CLEAN:
LAST VERIFIED TEST COMMAND / RESULT / RUN URL:
LAST WORKING RELEASE / APK SHA / SIGNING CERT DIGEST:
CURRENT PROBLEM AND EVIDENCE:
FILES MODIFIED AND WHY:
CHANGES MERGED OR STILL UNCOMMITTED:
OPEN ISSUES / PULL REQUESTS:
KNOWN KNOWN-GOOD FALLBACK:
NOT RUN (especially Samsung/Knox physical tests):
SECRETS / ACCESS NOT AVAILABLE (names only, never values):
NEXT SMALLEST UNBLOCKED TASK:
EXACT RESUME COMMAND OR CODEX PROMPT:
```

The next session must verify HEAD and compare ledger checkpoint to repository state before applying another patch. If someone else changed `main`, inspect the diff and regenerate test baselines; never overwrite their work silently. Store SHA and timestamps to detect duplicate tasks. If a previous attempt failed, write an explicit root-cause hypothesis and a regression fixture before retrying the same approach.

## Folio 85 — Fleet deployment acceptance / go-no-go authority

The release gate requires distinct approvals from engineering (source/build/test), IT/Knox administration (policy and install), and operations (workflow and appropriate field fallback). The system is safety-relevant but not a clinically validated medical device by default; do not imply regulatory certification. A go decision requires real Samsung pilot evidence for target device classes and clear fallback procedures for missed, silent or delayed reminders. No-go triggers: listener permissions inconsistent or ungrantable; lost pending alerts; one-and-done reproduces; silent reminders in the intended kiosk policy; ACK erases unrelated alerts; unbounded resource use; logging inaccessible for remote support; exported sensitive content; APK signature/in-place update mismatch; no reproducible rollback plan.

If one model fails and others pass, do not blanket-approve the fleet. Use policy-scoped device groups and a compatibility matrix. Treat ambiguous evidence as not run/inconclusive, not a pass. Accepting known degraded timing is an explicit operational decision, not a developer assumption.

## Folio 86 — Deep acceptance scenario library: catalog instructions

The numbered scenarios in the next tables are not test-execution results. Codex must implement deterministic portions and collect physical evidence for device-dependent portions. Each scenario must record `expected state`, `expected next alarm`, `expected audible result`, `expected ACK result`, `expected diagnostic evidence`, `test environment`, and `actual result`. A case that passes in Robolectric is still NOT RUN on Samsung until an actual device runs it. Where a scenario uses artificial hooks, restrict them to test builds. Matrix explosion should be controlled through pairwise testing followed by full critical-path coverage, not by skipping high-risk cases.

### 86A. Source and acknowledgment cases

| ID | Fixture/action | Acceptance property | Required observation |
|---|---|---|---|
| A-001 | One Google Messages notification | One pending; reminder repeats | Track → schedule → fire → post → rearm |
| A-002 | One Samsung Messages notification | Same behavior | Observed package and channel |
| A-003 | One AOSP MMS notification | Same behavior if deployed | Package classification |
| A-004 | One TraumaSoft MDT v1 notification | Eligible unless flags make it unsupported | Actual test-dispatch callback |
| A-005 | One TraumaSoft MDT v2 notification | Same | Actual test-dispatch callback |
| A-006 | Additional configured package | Recognized after policy change | Managed config effective value |
| A-007 | Package not configured | Ignored | No pending mutation |
| A-008 | Same key/time repost | No new logical item, no timer push | Due time before/after |
| A-009 | Same key newer generation | Correct latest generation | Stale removal safety |
| A-010 | Two SMS same thread | No unintended lost alerts | Count/identity contract |
| A-011 | SMS plus MDT simultaneous | Both pending | Shared chain continuity |
| A-012 | Three sources burst | Bounded work | No scheduling starvation |
| A-013 | Group summary only | Filter correctly | Skip reason |
| A-014 | Ongoing service notification | Ignore | Skip reason |
| A-015 | Incoming call | Do not create missed-call pending | No false positive |
| A-016 | Missed call | Create pending if supported | Classification evidence |
| A-017 | Answered call | No missed-call pending | No false positive |
| A-018 | Voicemail alert | No false missed-call match | Category/channel evidence |
| A-019 | Source app cancels silently | Retain pending under chosen policy | `source_removed_retained` |
| A-020 | User taps source notification | ACK corresponding generation if supported | Reason and marker |
| A-021 | User swipes source notification | ACK according to user-removal contract | Reason table |
| A-022 | Source notification cleared in bulk | Consistent per-item semantics | No unrelated losses |
| A-023 | Reminder tapped | ACK only designated generation | Pending count decrements one |
| A-024 | ACK ALL tapped | All pending cleared atomically | No further repeat |
| A-025 | Reminder swiped away | Intended all-ACK behavior | No stale callbacks |
| A-026 | ACK with disk failure | No false success | Store mutation/diagnostic |
| A-027 | Duplicate ACK actions | Idempotent | Count stable at zero |
| A-028 | Stale ACK for previous generation | New generation remains | Marker differentiation |
| A-029 | App cancel after reminder tap | No reanimation | Marker still effective |
| A-030 | Listener sync after explicit ACK | Already-ACKed item suppressed | `already_acked` classification |

### 86B. Alarm, device and system recovery cases

| ID | Fixture/action | Acceptance property | Required observation |
|---|---|---|---|
| B-001 | First due at configured 60 seconds | Correct scheduled mode | Due/fired monotonic pair |
| B-002 | Repost at 45 seconds | Due not postponed | Prior due preserved |
| B-003 | Second source at 50 seconds | First due preserved | Earliest alarm retained |
| B-004 | Ten cycles | No one-and-done failure | Ten fire/post/rearm chains |
| B-005 | Thirty cycles | No runaway leak or skipped timer | Resource/timing series |
| B-006 | Exact permission absent | Honest degraded fallback | Mode and blocker |
| B-007 | Exact permission revoked mid-cycle | Fallback/recovery | Error handled |
| B-008 | Exact scheduling throws SecurityException | Legal fallback or BLOCKED | Result logged |
| B-009 | Fallback scheduling also throws | Blocked, no fake due health | Failure diagnostics |
| B-010 | Alarm delivered twice | No double state mutation | Generation fencing |
| B-011 | Stale alarm after ACK ALL | No reminder | Empty pending check |
| B-012 | Notification post throws | Reschedule chain not severed | Future due or explicit block |
| B-013 | Logging throws in receiver | Core still runs | Repeat and schedule |
| B-014 | Listener disconnects | State retained; rebind requested | Connection events |
| B-015 | Listener reconnects repeatedly | No duplicate pending | Count stable |
| B-016 | Process killed normally | Pending survives | Restart evidence |
| B-017 | Device soft reboot | Defined coherent pending/ACK state | Boot epoch handling |
| B-018 | Full power cycle | Same | Monotonic reset safe |
| B-019 | App updated in place | Rebind/schedule restored | Package-replaced events |
| B-020 | Device idle 30 minutes | Repeat behavior measured | Power state and late_ms |
| B-021 | Device idle 24 hours | No silent stopped chain | Full timeline |
| B-022 | App force-stopped | Correctly documented limitation | Cannot claim automatic revival |
| B-023 | Device battery saver enabled | Timing and notification observed | Actual degrade state |
| B-024 | Wall clock jumps forward | Relative interval safe | Clock and due consistency |
| B-025 | Wall clock jumps backward | Same | No negative duration artifacts |
| B-026 | Time zone changes | Timeline consistent | Offset recorded |
| B-027 | DST transition | Retention and intervals valid | Unique segment timestamps |
| B-028 | Interval policy changes while pending | Defined rescheduling | Effective config/version |
| B-029 | Max repeats changed from zero | Explicit cap behavior | No unexplained silence |
| B-030 | Watchdog executes while receiver active | No race or duplicate | Lock/scheduler trace |

### 86C. Audible, permissions, kiosk and routing cases

| ID | Fixture/action | Acceptance property | Required observation |
|---|---|---|---|
| C-001 | Channel HIGH with sound | Physical audible test | Independent sound observation |
| C-002 | Channel importance NONE | Clear blocker | Channel importance logged |
| C-003 | Channel no sound URI | Clear blocker | Sound state logged |
| C-004 | Notifications disabled | Clear blocker | `areNotificationsEnabled=false` |
| C-005 | Runtime POST permission missing | Clear blocker | Permission result |
| C-006 | Listener grant missing | No fake monitoring PASS | Listener status |
| C-007 | Listener grant restored | Rebind and pickup | Active notifications sync |
| C-008 | DND priority-only mode | Behavior measured | Interruption filter |
| C-009 | DND total silence | No false audible PASS | Actual playback result |
| C-010 | Notification volume zero | No false audible PASS | Volume evidence |
| C-011 | Notification volume raised by Volume Guard | Sound tested | Independent route evidence |
| C-012 | Silent ringer mode | Behavior measured | Device setting |
| C-013 | Bluetooth audio route | Speaker/route determined | Route and sound |
| C-014 | Wired audio route | Same | Route and sound |
| C-015 | Samsung separate-app-sound setting | Routing verified | Policy/device capture |
| C-016 | Android notification cooldown enabled | Ten-cycle audible evidence | Actual sound repeats |
| C-017 | Notification cooldown disabled | Compare | Settings/evidence |
| C-018 | Kiosk notification bar allowed | Audible/reviewable result | Policy and test |
| C-019 | Kiosk notification bar disallowed | Detect actual limitation | Kiosk policy and sound |
| C-020 | Kiosk background app hidden | App still monitors | Process/listener trace |
| C-021 | App installed but never launched | Failure mode identified | Bootstrap evidence |
| C-022 | Knox auto-run configured | Actual launch verified | Enrollment trace |
| C-023 | Battery allowlist assigned but ineffective | Blocker identified | Effective device state |
| C-024 | Force-stop blocklist assigned | Actual enforcement observed | Policy/device evidence |
| C-025 | Headless launch flag set | No crew-facing persistent UI | Screen recording/manual inspection |
| C-026 | IT status activity remote launch | Access path verified | Remote Support steps |
| C-027 | IT ZIP export remote retrieval | ZIP on remote console | Location and integrity |
| C-028 | Update under kiosk | No lost monitoring | Rebind/ACK/pending trace |
| C-029 | Source SMS app notifications disabled | Blocker not misattributed | Source settings |
| C-030 | MDT source silent channel | Correct uncertainty reported | Source channel behavior |

### 86D. Storage, export, security and performance cases

| ID | Fixture/action | Acceptance property | Required observation |
|---|---|---|---|
| D-001 | Basic daily log creation | Date format and content schema | File inspection |
| D-002 | 14-day retention range | Retains correct dates | Simulated dates |
| D-003 | 15th-day prune | Correct oldest removal | File inventory |
| D-004 | Daily file reaches 3 MiB | Explicit bounded handling | No silent data loss |
| D-005 | Total logs reach 50 MiB | Bounded total | Disk usage |
| D-006 | Midnight concurrent append | Correct date segments | No event loss |
| D-007 | Clock rollback | No file collision/drop | Segment naming |
| D-008 | ZIP with 14 days | All listed files readable | Round-trip extraction |
| D-009 | ZIP interrupted midway | No partial final export | Atomic publish check |
| D-010 | Second concurrent export | Safe and bounded | Memory/pointer result |
| D-011 | Export while logging | Consistent snapshot or documented semantics | Integrity manifest |
| D-012 | File I/O exception | Alert path unaffected | Injected fault |
| D-013 | Storage nearly full | Explicit blocker | Storage-health event |
| D-014 | Malformed primary items JSON | Verified restore/block behavior | State preserved |
| D-015 | Malformed primary and backup | Explicit critical failure | No false empty queue |
| D-016 | ACK markers corrupted | No silent replay | Recovery state |
| D-017 | 4096 ACK entries | Bounded lookup time | Timing measurement |
| D-018 | 500 synthetic notification posts | No unbounded growth | PSS/CPU samples |
| D-019 | ZIP export memory peak | Bounded streaming | PSS measurement |
| D-020 | Delayed storage commit | No ANR | Callback latency |
| D-021 | Logs contain synthetic message title | Must NOT leak content | Privacy scan |
| D-022 | Logs contain synthetic phone number | Must NOT leak | Privacy scan |
| D-023 | Malicious package field formatting | No log forging | Sanitizer test |
| D-024 | Untrusted export filename | No ZIP traversal | ZIP entry name validation |
| D-025 | Export after package update | Readable legacy logs | Backward compatibility |
| D-026 | Reinstall/clear app data | Document actual state loss | IT warning |
| D-027 | Signed release APK | Fingerprint matches intended key | Artifact evidence |
| D-028 | Update from previous version | No install signature failure | Managed update pilot |
| D-029 | Rollback using newer versionCode | Restored working behavior | Device acceptance |
| D-030 | Debug-only test hooks | Absent/inactive in release | APK/source inspection |

## Folio 87 — Scenario coverage algorithm for Codex

The 120 cases above are **requirements for evidence**, not 120 currently passing tests. Assign execution layer per case. Critical cases A-001, A-004, A-019, A-023, A-024, B-004, B-006, B-017, B-020, B-022, C-001, C-016, C-018, C-020, C-027, D-008, D-014, D-021 and D-027 must be handled explicitly before pilot completion. Derive additional combinations from device × OS × kiosk × source × screen state × permission state × ACK path. Use pairwise selection for routine interactions but full enumeration for loss-of-alert and incorrect-ACK combinations. Add negative cases for each security boundary. Every test row must include `test_id`, `source_sha`, `build_artifact`, `environment`, `actions`, `assertions`, `result`, `evidence`, `last_run`, `owner` and `next_action`. Never inflate progress by counting a specification row as a test result.

## Folio 88 — Implementation review checklists by code ownership

**Listener reviewer:** Verify permission status, connected/disconnected callbacks, active notification sync, ignored self-notifications, package watchlist, missed-call classifier, group/ongoing filtering, generation ID, source removal reasons, dedup, lock timing and safe logs. **Scheduler reviewer:** Verify exact API eligibility, fallback semantics, one logical alarm, fixed PendingIntent identity, due time fencing, monotonic clock/reboot, overdue repair and cancel. **Receiver reviewer:** Verify empty-queue handling, repeat limit, `try/finally` continuation, at-most-once logical repeat, exception isolation, responsiveness and watchdog rescue. **Store reviewer:** Verify crash consistency, JSON validation, backups, schema migration, bounded markers, ACK atomicity, performance and no privacy leaks. **Notifier reviewer:** Verify channel create/update policy, notification sound/importance, id alternation, click targets, ACK ALL and swipe behavior, permissions and accurate logs. **EventLog reviewer:** Verify data minimization, bounds, rotation, export integrity, concurrency and remote accessibility. **Knox reviewer:** Verify actual managed config, enrollment sequence, special access grants, kiosk background inclusion, sounds and support export on representative firmware. **Release reviewer:** Verify signed artifact provenance, CI evidence, release gates and rollback. Each reviewer produces a signed-off checklist in the engineering ledger or a clearly documented blocker.

## Folio 89 — Engineering handoff lifecycle and permanent continuity

This dossier must exist in the repository so that it survives loss of any chat history. Create `docs/MASTER_ENGINEERING_DOSSIER.md` from this file, commit it to `main` through the approved workflow, and link it from README. Maintain a concise top-of-file `CURRENT STATUS` section with verified HEAD, latest release, outstanding blockers and next task while retaining the extensive design history below. Never overwrite verification evidence with future tense. A status update is valid only when the ledger records the command, outcome, SHA and proof. Use semantic change notes so Codex can understand why a design choice was made and reverse it when evidence demands. The dossier is controlling context, but it cannot supersede current repository code or validated device evidence.

## Folio 90 — Escalation boundaries and deployment safety

Engineering may autonomously refactor, test, add diagnostics and produce pilot APKs within the repository, but cannot unilaterally override company safety procedures, deploy to active clinical fleet, alter enterprise Knox policies, create unsupported accesses, change signing trust roots, destroy production data or declare clinical sufficiency. Any such action requires the appropriate operations/IT approver. If a technical requirement is physically impossible within Android restrictions, present the exact reason, the best supported fallback, measurable effects and a decision record. Do not conceal an OS limit by changing wording in tests. A smaller but verified capability is more valuable than an implausible promise of unconditional alerts.

## Folio 91 — Master resumption message: copy directly into Codex

```
You are the lead engineer taking over DevTunaSoft/missed-alert.
Read docs/MASTER_ENGINEERING_DOSSIER.md in full, including Folios 01–91,
and reconcile it with the current default-branch HEAD before any edits.

1. Produce a verified repo inventory, commit SHA, tests/workflows, actual status and
   discrepancies between docs and code. Do not trust inherited green claims.
2. Reproduce or conclusively narrow the alert-fires-once-but-does-not-repeat issue;
   trace listener → pending store → alarm → receiver → notification → rearm.
3. Implement the minimum safe change, add targeted regression, and run available
   tests/build/lint. Fix root causes, not symptoms. Check memory and power impact.
4. Confirm explicit ACK semantics and guard against source-app silent cancellations,
   reused notification keys, duplicate callbacks and race conditions.
5. Validate diagnostics retention, ZIP integrity and privacy; ensure Knox Remote
   Support extraction works in a managed Samsung lab, or label hardware NOT RUN.
6. Maintain README, dossier, engineering ledger, roadmap, user manual, feature
   status, test matrix, Knox runbook and NEXT_SESSION.md as living evidence.
7. Prefer mainline integration and avoid permanent branch sprawl; protect secrets,
   signed releases and production devices. Never fabricate test/device evidence.
8. Continue in productive checkpointed development cycles until genuine blocker,
   repo permissions, session limits or completed verified objectives stop progress.
9. Before stopping, leave exact HEAD, files changed, tests/results, remaining
   blockers and the next one-command or one-prompt resumption instruction.

Priority is a dependable, audibly repeating, low-memory, low-battery reminder in
managed Knox kiosk, not a high number of commits or pages of documentation.
```

## Folio 92 — Evidence ledger: current dossier preparation

| Item | Observed evidence | Status | Caution |
|---|---|---|---|
| GitHub account repository | `DevTunaSoft/missed-alert` was returned by connected repository listing | OBSERVED 2026-11-12 | Reconfirm permissions at takeover |
| Recent commit | `fe19287723293c017ed9fa595863908941edf47d` | OBSERVED via commit search | May no longer be HEAD |
| Branches | `main`, `pilot/xcover-pro-reminder-recovery` | OBSERVED via branch search | Branch search not PR audit |
| Gradle | Android plugin 8.13.2, Kotlin plugin 2.3.10, compileSdk 36 | OBSERVED file content | Does not imply successful build |
| App | `com.crewalert.missedalert`, version 1.2.4-pilot, minSdk 29, targetSdk 36 | OBSERVED file content | Device installation not verified |
| Source | `MissedAlertListener`, `Scheduler`, `ReAlertReceiver`, `AlertStore`, `Notifier`, `EventLog`, `RecoveryJobService`, `Config`, `NotificationRemovalPolicy` | OBSERVED code content | Remaining source files and tests require inspection |
| Logging | 14-day nominal retention, 50 MiB total, 3 MiB/day, streamed ZIP | OBSERVED implementation | Rotation data-loss concern; not load tested here |
| ACK | User-related source removal acknowledges; app removal retained | OBSERVED implementation | Full ACK action handlers not inspected here |
| Scheduling | `setAlarmClock` exact path; `setAndAllowWhileIdle` fallback | OBSERVED implementation | Physical interval not verified |
| Build outcome | Not executed in dossier preparation | NOT RUN | No build PASS assertion permitted |
| Device/Knox | No physical device test executed in dossier preparation | NOT RUN | No audio or production readiness assertion |
| ZIP export | Implementation code read; ZIP not executed | NOT RUN | Integrity unverified |
| Full repo history/PRs | Not exhaustively inspected | INCOMPLETE | Codex must reconcile |

**Closeout:** This remains an unusually detailed *engineering takeover specification*, not a substitute for code inspection and real tests. The most important continuing work is verifying which alert path is broken and proving recovery on the Samsung/Knox matrix. Maintain quality and factual precision above document length.


---
# PART III — VERIFIED-STATE TAKEOVER, ENGINEERING ARCHAEOLOGY, AND AUTONOMOUS EXECUTION CONSTITUTION

**Edition IV supplement — 2026-11-12.** This part is an execution-ready continuity protocol. It is not a claim that any code was modified or tested in preparing this document. All statements labeled `REPO-OBSERVED` are based on specific source files or GitHub commit metadata retrieved on 2026-11-12. Commit subjects establish *developer intent*, not correctness or the exact changed lines. `REPORTED` identifies an operational observation from the project discussions; `INFERENCE` is a hypothesis; `NOT RUN` identifies missing validation. Codex must replace hypotheses with traceable evidence, never with confidence alone.

## 1. First-read contract for the incoming Codex engineer

This is the first instruction to follow, before proposing architecture or making edits. Work in `DevTunaSoft/missed-alert`, not `MMT-Volume-Guard`, `MMT-Device-Provisioner`, or the unrelated browser project. Treat `main` as the release integration target. Inspect actual repository state, protected-branch rules, commits, workflows, existing issues, and any in-flight work before editing. Use short-lived isolated working changes only where tooling requires them; do not multiply long-lived product variants. Never force-push or bypass required checks. Do not publish production APKs without evidence and approval appropriate to release risk.

Mission: on Samsung Knox-managed phones, repeat qualifying Google/Samsung Messages, TraumaSoft MDT, and missed-call notifications at the configured interval (nominal 60 seconds) until an appropriate, verifiable acknowledgment. Crew must not need to launch or configure this helper app; the helper must remain hidden from kiosk navigation while operating. The app must survive ordinary background execution and recover from interruptions within Android's real platform constraints. It must produce daily privacy-preserving diagnostics IT can export during Knox Remote Support, without ADB on production devices. It must be lean on RAM, CPU, battery, disk, and dependencies. Device-specific behavior cannot be certified by JVM unit tests alone.

**Nonnegotiable operational hierarchy:** (1) do not lose or silently acknowledge an actionable alert; (2) do not falsely claim an audible reminder; (3) preserve alert state and recovery; (4) make every failure diagnosable; (5) minimize crew interaction; (6) minimize resource use; (7) minimize complexity. A change that improves test pass counts but violates these principles is not an improvement.

### 1.1 Autonomous operating loop

At the start of every Codex session: inspect current `main` SHA, recent history, working tree, latest CI, open PRs/issues, release artifacts, current dossier, and task ledger. Confirm no other agent is editing the same file. Choose the highest-value unblocked task; define acceptance criteria and a reproducible failure before editing; make the smallest change; add regression tests; run all feasible checks; inspect diffs and security/performance impact; commit only coherent verified work; update dossier, ledger, and handoff; repeat while execution remains available. If an environmental blocker occurs, pivot to another unblocked useful task instead of stopping or asking for routine permission. Stop only at genuine permission/access constraints, physical-device evidence, unresolved consequential requirements, safety risk, or execution limits. Never claim continuous work after the session ends.

The Codex handoff must answer, without further user input: **What is the mission? What code exists? What was changed and why? What has been proven? What remains broken? What can be worked on next? How is success measured? How can the next session resume exactly?**

## 2. Historical source hierarchy and reconstruction methodology

Primary evidence: code and diff at a specific SHA; build/test job log with run ID and timestamps; APK manifest/signing inspection; on-device logs and controlled observation. Secondary evidence: commit message, PR description, issue, README, engineering notes. Tertiary evidence: chat-reported symptoms and design discussions. A historical claim must carry its evidence tier. A commit message saying 'fix' proves that a fix was *attempted*, not that the defect was eliminated. Do not backfill invented motives for old commits. If the motive is inferred from the code and adjacent commit sequence, label it `RECONSTRUCTED RATIONALE — VERIFY`.

Codex should construct a complete timeline for every reachable commit, including parents, changed paths, diffstat, behavior delta, linked tests, CI outcomes, relevant issue/PR, and rollback SHA. The 89 commits returned by the GitHub commit listing on 2026-11-12 are a *retrieved window*, not a guarantee of complete reachable history. Re-run `git log --all --date=iso --reverse --format=...`, compare all branch tips, check tags and releases, and identify merge commits and rewritten history. The observed latest listed SHA was `fe19287723293c017ed9fa595863908941edf47d` (subject: Correct ZIP device status line breaks). The observed branches were `main` and `pilot/xcover-pro-reminder-recovery`; do not assume their tips are identical or that the pilot branch is merged without checking.

### 2.1 Verified commit-subject chronology: project creation and initial scope

| Observed commit prefix | Subject / intent from metadata | Why it matters; interpretation to verify |
|---|---|---|
| `8ee6de523f6e` | Initial 60-second repeating missed-alert app for MDT/SMS | Establishes first functional mission; determine original acceptance assumptions and alarm mechanism |
| `ab2cc6a85723` | CI uses preinstalled Android SDK | Early response to build environment reliability/cost; verify workflow behavior |
| `5b8a798daa4a` | Add missed calls and zero-touch Knox rollout | Scope expanded beyond SMS/MDT; test false-positive incoming calls |
| `21205a2388c2` | Stable 'latest' release download link | Fleet deployment convenience; validate signing and provenance |
| `f0e090daedeb` | Align SDK/build tooling with Volume Guard | Shared fleet maintenance expectations, not shared runtime state |
| `c6046c78bb43` | Sign release from GitHub secrets with fallback | Release trust risk: debug fallback must not be mistaken for production-signed APK |
| `8c7a988d7ed4` | Hidden/kiosk test plan | Background and kiosk acceptance became explicit |
| `a0a156d15413` | Privacy-safe event log and clock-change rearming | Diagnosability and temporal recovery expanded |
| `f4990e80a833` | Stress test plan and burst script | Burst/concurrency reliability was recognized |
| `ed34e9c02eaa` | Export log to Downloads | IT recovery workflow introduced |

### 2.2 Verified commit-subject chronology: reliability and XCover integration

| Prefix | Subject / intent | Why it matters; investigate |
|---|---|---|
| `618deb19b87c` | Integrate XCover Pro, alarm recovery, static/lint/test checks | Min SDK expansion and merge behavior; inspect branch provenance |
| `890df7b7114a` | Rearm after new alerts, partial ACK, reboot | Repeating chain and state transitions were failure-prone |
| `cc8a6ebb25d1` | Eight calendar days of logs | First expanded retention policy |
| `e1d4950d4e07` | Repair test newlines blocking build | Prior changes introduced syntactic regression |
| `f9027099b16b` | Harden ACK, export failures, overdue alarm diagnostics | Multi-domain failure hardening |
| `57212ca187c6` | Correct Kotlin line endings | Source formatting/build integrity issue |
| `eb3f8a8db185` | Restore export-header escaped newlines | Export correctness regression |
| `ff48bee368a2` | Fix diagnostic header string escapes | Another export formatting regression |
| `3cb3968bfd75` | 500k-transition fuzz model and ACK replay fix | Model-based stress does not prove Android device behavior |
| `a8cd9ae16794` | 2M-transition fuzz model and retention boundaries | Large synthetic test scope; inspect coverage realism |
| `09c9b730c91b` | Harden alert JSON corruption | Persistence loss was recognized |
| `7011b4421ded` | Clear stale alarm at repeat limit | Lifecycle cleanup |
| `618deb19b87c` | XCover integration and recovery | Confirm same commit not counted twice in full history |

### 2.3 Verified commit-subject chronology: concurrency, persistence, acknowledgments

| Prefix | Intent | Investigative consequence |
|---|---|---|
| `618416930556` | Malformed persisted-state fault injection | Check assertions for fail-closed behavior |
| `fa03a728cc27` | Fix randomized corruption test interpolation | Test quality itself required correction |
| `d0cddd42a367` | Concurrent store recovery tests | Verify synchronized boundaries and multi-process assumptions |
| `717bb9b493d3` | Expose Robolectric failure traces | CI observability |
| `8242f25a54e3` | Null restrictions-service crash prevention | Knox config may be unavailable at runtime |
| `59a4e3228f76` | Concurrent counters and ACK markers fuzz | State race regression protection |
| `cbc55cf5df8b` | Re-arm on listener reconnect | Notification listener binding is not guaranteed continuous |
| `13edaca92670` | Boot diagnostics and corrupt-storage isolation | Boot failure should be diagnosable, not silently clear alerts |
| `95558846905c` | Boot/package update tests and Knox rollout | Deployment lifecycle coverage |
| `624c7355f6d1` | Notification-generation ACK and lifecycle fuzz | Reused notification keys risk wrong ACK |
| `6887f2ec0a2a` | Serialize removal cancellation and new-alert processing | Race between last ACK and new alert |
| `75840a7c1cce` | Prevent stale reminder tap clearing newer alert | Generation-aware acknowledgment |
| `944bf6872c2b` | Atomic bulk ACK and marker retention | Crash consistency |
| `c595886a10ee` | Recover corrupted JSON from snapshots | Recovery without crew action |
| `afe09b9eaa9e` | Update regression for snapshot recovery | Tests aligned with recovery policy |
| `9af612bdf9b4` | Reject malformed backup records | Valid JSON is not necessarily valid state |
| `007a11e65297` | Fail on unsuccessful persisted writes | Avoid silent data loss |
| `e94f49404fed` | ACK backup recovery and 900-alert storm fuzz | High-burst reliability, not device certification |
| `76d20d0b8740` | Corruption regression fail-closed | Corruption must not silently acknowledge |
| `ea632e41651f` | Correct notification dismissal semantics | App/system removal versus user action is safety-critical |
| `f62879b3442c` | Always clean up/rearm after delivery faults | Reminder failure must not sever alarm chain |

### 2.4 Verified commit-subject chronology: Knox bootstrap, releases, and UI

| Prefix | Intent | Why it matters |
|---|---|---|
| `ba0b1b57cce1` | Invisible Knox auto-run bootstrap | App must initialize without visible crew UI |
| `416b615cc6d9` | Manifest-based launcher test | Robolectric package resolution insufficient |
| `51ba864cb4a3` | Immediate unattended initialization and repeat startup test | No manual app open during enrollment |
| `e8d0bd9e95f7` | Pilot v1.2.0 / versionCode 3 | Signing/version upgrade lineage |
| `8aa43c0689b3` | App icon and APK verification / v1.2.1 | Knox 'invalid app' class of packaging issue |
| `0f8bf3313409` | Manual IT status vs managed silent launch | Separate IT and crew experience |
| `e209544dc811` | Readiness dashboard and immediate test | Diagnose configuration without touching real alert queue |
| `fc4f1b69c862` | Managed config resource linking | Manifest/resource packaging |
| `64db85747a67` | Manual-launch dashboard regression | UI behavior test |
| `bcc534bb68a9` | APK optimized icon verification / v1.2.2 | Packaging validation |
| `604ed0ba202f` | Restrict IT screen and test readiness | Diagnostic access security |
| `48a79669c6f5` | Zero-crew Knox operation and permission blockers | Explicit fleet constraints |
| `8797a7c06349` | Shell-protected IT diagnostics and watchdog blockers | Controlled diagnostics entry |
| `ac1dda607fde` | Pilot versionCode 6 | Update path |
| `5058c18dba2b` | No-ADB fleet constraint and remote diagnostics | IT must retrieve logs through Knox Remote Support |
| `081cfe34adcc` | Preserve alarm deadline during notification bursts | Avoid postponing reminder indefinitely |
| `7e5a9490f788` | Repeat-continuity release version and serialized builds | Fix rollout integrity |
| `064bd4e552bf` | Fix test Android API constructor | Test compatibility correction |
| `3b79f9bc9296` | Fix test Process.myUserHandle | Test compatibility correction |

### 2.5 Verified commit-subject chronology: 2026-11-12 diagnostic hardening

| Prefix | Intent | Why it matters |
|---|---|---|
| `a758a06cb39b` | Preserve schedule on single-alert ACK | Multiple pending alerts must continue |
| `06bd6ce02651` | Regression for partial ACK deadline | Prevent recurrence |
| `d4c24a81f6c7` | 14-day bounded log retention | Retention changed from 8 to 14 days |
| `6b2dc32ec456` | Update regression contracts for retention | Contract and implementation alignment |
| `8812cc6c4f31` | Passive watchdog health checks | Avoid production synthetic alert mutation |
| `82628d53ab46` | Streaming ZIP diagnostics | Bound export memory |
| `ae9a41e948ed` | IT diagnostics use streaming ZIP | End-to-end export integration |
| `52617d57f964` | Isolate IT delivery test from real queue | Prevent test-induced acknowledgment/loss |
| `262948b05652` | Regression for isolated IT test | Explicit safety verification |
| `1f220424503b` | Log battery, alarm, DND and sound state | Diagnose silent alerts |
| `b6d1d34a1c28` | Verify watchdog recovery in same cycle | Recovery must have an observable result |
| `30af7c601114` | Recover overdue alarm with short grace | Do not delay full interval after interruption |
| `370765a5f506` | Overdue recovery regression | Timing behavior verification |
| `cc136f661758` | Advance Robolectric elapsed clock | Repair timing test setup |
| `3c176b608c05` | Bound daily log growth | Prevent uncontrolled disk use |
| `22dfc990383e` | Preserve active log during pruning | Avoid deleting current evidence |
| `f93a57ece775` | Actionable Knox permission/channel blockers | Diagnosable deployment issues |
| `db1146ad9ff0` | Distinguish listener permission vs disconnected | Different remediation paths |
| `9749dcf83ed0` | Missing channel detection and recreation | Recover notification channel where allowed |
| `730530b4e8ce` | Channel recovery regression tests | Avoid unsafe channel mutation |
| `64afae3ff8c9` | Separate alarm recovery from diagnostic failures | Observability must not break core function |
| `8256410bbd4f` | Retention and ZIP integrity tests | Export contract verification |
| `f28f2245418a` | Isolate retention test from cached prune date | Test determinism |
| `5adaf01704b0` | Daily rotation and continued logging tests | Detect retention/rotation data loss |
| `0c12777a9283` | Prevent partial ZIP appearing in Downloads | Atomic-ish user-visible export behavior |
| `509b7e0d72a3` | Preserve status newlines in export | Human-readable diagnostic header |
| `fe1928772329` | Correct ZIP status line breaks | Follow-up formatting correction |

**Required follow-up:** For each entry, retrieve diff and affected tests. Record original failure evidence, actual design alternatives if documented, test results at the time, subsequent regressions, and superseding commits. A chronological subject ledger is a useful map, not a substitute for archaeology. Some initial subjects include co-author metadata; do not attribute individual authorship or motive beyond evidence.

## 3. Actual code map and component contracts (source inspected 2026-11-12)

`app/build.gradle.kts`: `applicationId=com.crewalert.missedalert`, `minSdk=29`, `targetSdk=36`, `compileSdk=36`, `versionCode=7`, `versionName=1.2.4-pilot`, AGP 8.13.2, Kotlin 2.3.10, Java 17, JUnit and Robolectric; no production third-party dependencies declared. `README.md` still contains an early Android-13+/minSdk-33 statement and an early 'source removal = ACK' description, while later paragraphs describe Android 10+ and source app removal retained. **Documentation contradictions are observed and must be resolved against current code and approved requirements.**

`MissedAlertListener.kt`: listener bind, active notification synchronization, watched-package detection, missed-call filtering, pending queue updates, and user-dismissal recognition. It ignores own-package notifications and ongoing/group-summary entries; it stores metadata but deliberately does not log titles/bodies. On listener reconnect it requests scheduling recovery and synchronizes active notifications. **Review risk:** `onNotificationRemoved` retains alerts on source-app/system removal; is that always appropriate after an MDT has been handled? Resolve with real operational ACK semantics and evidence; do not silently change behavior.

`Config.kt`: watched packages include `com.traumasoft.mdt`, `com.traumasoft.mdtv2`, `com.samsung.android.messaging`, `com.google.android.apps.messaging`, and `com.android.mms`. Missed-call detection uses a separate dialer package set and category/text heuristics. Managed restrictions include `extra_packages`, `headless_on_launch`, `interval_seconds` (default 60; minimum 15), and `max_repeats` (0 unlimited). **Review risk:** text heuristics for missed calls may be locale-dependent and spoofable; verify against real dialer notifications without logging personal text.

`AlertStore.kt`: SharedPreferences JSON queue, backup snapshots, ACK markers, repeat count, monotonic `nextAt`, and seen-package list. Writes often use `commit()` for crash consistency; some return values are not checked (e.g., repeat count, nextAt), so inspect whether a failed write could cause duplicate or missing reminders. `Item` includes title/text fields but the serialized pending queue currently writes package and post time, not the title/text; reconcile the intended behavior after process recovery. `MAX_ACK_MARKERS=4096` can permit replay of sufficiently old notifications; test bounded-marker tradeoff and stale generation behavior.

`Scheduler.kt`: single PendingIntent request code 1; `setAlarmClock()` when exact alarms allowed, `setAndAllowWhileIdle()` fallback; persisted monotonic deadline; overdue recovery with a one-second grace. **Review risk:** `SystemClock.elapsedRealtime()` resets on reboot while SharedPreferences survives; examine `BootReceiver` to prove stale deadline handling and pending-state reconciliation. **Review risk:** `isPending` uses a persisted deadline plus PendingIntent existence; that is not proof of successful future delivery. **Review risk:** exact-alarm capability and alarm-clock API behavior vary by Android/Knox; test on devices. An inexact fallback cannot be advertised as guaranteed 60-second cadence.

`ReAlertReceiver.kt`: reads queue, logs alarm firing, posts reminder, increments repeat counter, re-arms in `finally`, schedules watchdog. Failure to read pending storage preserves state and defers recovery. **Review risk:** `max_repeats` behavior must not leave unacknowledged operational alerts permanently silent without an explicit policy decision; default unlimited is safer. Confirm synchronous receiver work remains short enough to avoid ANR and battery penalties.

`Notifier.kt`: high-importance `realert_v2` channel with default notification tone, vibration, alternating notification IDs 4242/4243 to prompt repeat sound, 'ACKNOWLEDGE ALL' action, and source-app open action. **Review risk:** a successful `NotificationManager.notify()` does not prove sound or vibration; channel settings, cooldown, DND, route, kiosk restrictions, and OEM behavior must be checked. **Review risk:** alternate-ID cancellation may trigger removal callbacks; verify own-package ignore and correct action PendingIntent updates. **Review risk:** source-app open action must not falsely acknowledge an alert when the target app fails to launch.

`NotificationRemovalPolicy.kt`: `REASON_CLICK`, `REASON_CANCEL`, `REASON_CANCEL_ALL`, `REASON_LISTENER_CANCEL`, `REASON_LISTENER_CANCEL_ALL` are treated as user dismissal. **Review risk:** listener-initiated cancellations can be non-crew actions; confirm actual platform semantics and intended definition of acknowledgment. `README.md` earlier states source notification removal always acknowledges; current code does not. Record explicit approved acknowledgment truth table.

`RecoveryJobService.kt`: periodic 15-minute persisted JobScheduler job; records listener permission, posting permission, notification/channel state, battery optimization, exact alarm access, interruption filter, pending count, and overdue state; attempts listener rebind and alarm recovery. **Review risk:** JobScheduler is deferred in Doze and is not a 60-second heartbeat. An unbound listener cannot observe source alerts during the gap; detect and report rather than promise impossible continuous interception. The watchdog's self-test is passive, not a substitute for end-to-end notification delivery.

`EventLog.kt`: app-specific daily files, 14-day calendar retention, 3 MiB/day cap, 50 MiB total cap, ZIP streaming with 8 KiB copy buffer, and no message bodies/titles/phone numbers in log event fields. **Observed concern:** when today's file exceeds its limit, `daily.writeText(...)` overwrites that day's earlier events with a rotation marker, losing forensic continuity. Replace with numbered segments or an explicit bounded ring policy that preserves oldest/newest according to documented priorities; test day boundary, concurrent writes, disk-full, failed export, ZIP validity, and log collection after crash. The total cap can force earlier-than-14-day deletion under sustained load; document that the retention target and byte caps can conflict and define which guarantee wins. Export uses a synchronized lock; stress memory and latency so export does not block critical reminder processing.

`AndroidManifest.xml`: listener, alarm receiver, acknowledgment receiver, boot receiver, recovery job, invisible bootstrap, IT status screen, DUMP-permission-protected IT diagnostic activity, notification/exact alarm/battery optimization permissions. **Review risk:** `POST_NOTIFICATIONS`, notification listener access, exact alarm special access, and kiosk policies require effective permission verification; manifest declaration alone is not grant. Check receiver exported state, pending intent mutability, diagnostic activity protection, and no visible crew entry point under assigned managed configuration.

**Uninspected source inventory:** `BootReceiver.kt`, `AckActivity.kt`, `AckReceiver.kt`, `BootstrapActivity.kt`, `ItDiagnosticsActivity.kt`, `StatusActivity.kt`, tests, resources, workflow files, and release scripts. These must be read before making production claims. No build, tests, emulator run, or device test were performed while compiling this book.

## 4. Requirement-to-code-to-evidence traceability standard

Each requirement receives stable ID, owner, acceptance criterion, code location, test IDs, latest evidence, platform matrix, and disposition. Seed entries:

| ID | Requirement | Code evidence | Mandatory acceptance | Current proof |
|---|---|---|---|---|
| R-001 | Detect Google Messages SMS | `Config`, `MissedAlertListener` | Real SMS, locked screen, listener bound | Code observed; device NOT RUN |
| R-002 | Detect TraumaSoft MDT alerts | same | Real MDT dispatch alert on fleet app | Code observed; device NOT RUN |
| R-003 | Repeat until appropriate ACK | `Scheduler`, `ReAlertReceiver`, `Ack*` | ≥5 audible repeats and stop after ACK | Partial code observed; device NOT RUN |
| R-004 | Hidden zero-crew kiosk operation | `BootstrapActivity`, manifest, Knox config | Knox install/update/reboot without crew UI | Manifest observed; fleet NOT RUN |
| R-005 | Survive Doze where platform allows | `Scheduler`, `RecoveryJobService` | 30-min idle + multi-hour real-device trace | Code observed; device NOT RUN |
| R-006 | Preserve multiple pending alerts | `AlertStore`, listener, ACK | Partial ACK doesn't clear others | Code observed; tests NOT RUN here |
| R-007 | Recover after process death/reboot | `BootReceiver`, listener, scheduler | Reboot/restart with pending items | Boot receiver NOT INSPECTED |
| R-008 | IT log retrieval without ADB | `EventLog`, IT diagnostics | Knox Remote Support export ZIP, open/verify | Code observed; device NOT RUN |
| R-009 | No sensitive message text in logs | `EventLog`, callers | Canary-data scan of all exports | Source review partial; test NOT RUN |
| R-010 | Bounded resource use | core code, Gradle | Measured idle/alert CPU, RSS, battery, disk | No device benchmark |
| R-011 | Valid signed APK for Knox | Gradle, CI | Install/upgrade/signature verification | Build workflow NOT INSPECTED |
| R-012 | Missed calls only, not active calls | listener/config | Dialer matrix, no incoming-call false alarms | Code observed; device NOT RUN |
| R-013 | Robust self-diagnostics | watchdog, EventLog | Fault injection and IT-readable reason | Code observed; device NOT RUN |
| R-014 | Correct silent/blocked indication | notifier, watchdog | Disable posting/channel/DND and observe | Code observed; device NOT RUN |
| R-015 | 14-day diagnostic retention | EventLog | Day boundary, high volume, storage cap | Code observed; tests NOT RUN here |

Extend to every existing test and requirement. Use statuses `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`, `INCONCLUSIVE`; do not collapse `BLOCKED` and `NOT RUN` into `PASS` or infer device success from CI.

## 5. Operational acknowledgment policy: an explicit decision that cannot be guessed

A crew may read an SMS or MDT dispatch without the originating app removing its notification; an app may remove its own notification without the crew reading it. Therefore 'notification removed' is not equivalent to 'human saw alert'. The application cannot prove a human read a dispatch merely by observing Android notification events. Design and document an operationally approved proxy for acknowledgment. Candidate events include tapping the source notification, tapping the reminder, explicitly pressing ACKNOWLEDGE ALL, dismissing a reminder, or source-app-specific handled state if an integration exists. Each has false-ACK risks. Until policy is approved, preserve the current code's conservative behavior and surface uncertainties. Never silently equate source-app cancellation with verified crew acknowledgment. Do not invent an MDT API or access to TraumaSoft internal state.

Define a truth table for: source notification clicked; source swiped; source app programmatically cancels; source notification replaced with same key/new post time; reminder tapped; reminder swiped; ACK ALL; device reboot; source app crash; notification listener disconnect; system clears shade; group-summary change; notification auto-expiry; missed call cleared; two concurrent sources; IT synthetic test. Each row requires 'pending queue mutation?', 'ACK marker?', 'schedule cancel/rearm?', 'audit event?', 'risk of false ACK?', 'test ID?'. Document accepted semantics before changing production behavior.

## 6. Device and fleet evidence collection protocol

Inventory each test device: manufacturer, model, SKU, OS build, One UI, security patch, Knox Manage version, Knox Service Plugin version, managed profile/device-owner state, kiosk mode, installed package/version/signing fingerprint, notification listener grant, posting grant, channel importance/sound, exact-alarm access, battery exemption, force-stop policy, DND state, notification cooldown, volume/route, and source app package/version. Redact serials, phone numbers, patient data, message bodies, and fleet identifiers from shared reports.

For each scenario, record device ID pseudonym, run timestamp, tester, preconditions, exact stimulus, expected observable behavior, observed audible/vibration/display behavior, notification timestamps, alarm timestamps, ZIP path/hash, screenshot/video if permitted, pass/fail rationale, and cleanup. At least five repeat cycles are required for each core source under awake, screen-off, idle/Doze, and kiosk. Include multi-hour idle, reboot, package upgrade, listener revocation, posting disabled, channel muted, DND, Bluetooth/wired route, low storage, process death, concurrent alerts, and partial acknowledgment. Test on A14/A15/A16/A17 and XCover Pro where supported; do not extrapolate across all Samsung firmware from one device. A laboratory emulator is valuable for functional tests but cannot certify Knox kiosk audio or OEM background policies.

If physical-device evidence is unavailable, Codex must keep coding/test hardening on unblocked tasks, generate a reproducible IT pilot protocol, and report device validation as `BLOCKED — requires managed Samsung device`. It must not repeatedly ask the user for permissions or access already specified in the dossier.

## 7. Detailed incident and regression playbooks

**Alerts once, then never again:** Reproduce with a real source notification. Compare `alert_tracked`, `alarm_scheduled`, `alarm_fired`, `reminder_posted`, and next `alarm_scheduled` in time order. Inspect `nextAt`, pending count, repeat counter, exact-alarm grant, and receiver execution. Distinguish listener failure, scheduling failure, notification posting failure, muted sound, and acknowledgment mutation. Add targeted tests for each observed root cause. Confirm at least five repeats under screen off; do not assume `notify()` equals sound.

**No alert ever:** Check effective listener access, posting permission, source app notifications, watched package name, bootstrap first launch, force-stop, kiosk notification policy, listener binding, source notification channel, and missed-call classification. A permission policy *assigned* in Knox is not necessarily *effective* on the device. Log the exact failing gate without private notification text.

**Alert remains after crew handles it:** Compare source notification key/post time, removal reason, explicit reminder ACK, and current pending queue. Verify whether TraumaSoft removes/updates its own notification after handling. Avoid changing acknowledgment semantics globally to fix one app-specific case without regression coverage.

**Wrong alert acknowledged:** Test two simultaneous source alerts, a reused key with new generation, delayed old removal, tapping the combined reminder, ACK ALL, swipe dismiss, and source launch failure. Validate that each action affects only the intended generation, except explicitly approved bulk actions.

**App seems dead after reboot or kiosk update:** Check package installed/enabled, signature continuity, first-launch bootstrap, boot receiver events, listener grant, watchdog scheduled, process/force-stop state, and persisted queue. A force-stopped Android package may not receive normal broadcasts until explicitly launched; no app code can promise recovery from all device-policy states.

**Log export incomplete:** Test bounded daily files, retention across timezones/DST, rotation at cap, concurrent event logging during export, low disk, cancelled export, partial file prevention, ZIP CRC/entry integrity, and device-status newlines. Preserve source logs on export failure; do not block alert delivery for diagnostics.

**Knox rejects APK:** Compare application ID, minSdk, target SDK, signature certificate lineage, versionCode monotonicity, package manifest, resource/icon references, and policy assignment. Separate build failure, unsigned APK, mismatched signing identity, Knox upload rejection, and install/update rejection. Do not solve signing problems by publishing secrets or disabling verification.

**High battery or RAM:** Capture baseline with app idle, listener bound, pending alerts, repeated notifications, and ZIP export. Measure memory PSS/RSS, CPU wakeups, scheduled job count, alarm count, disk writes, and battery drain under controlled conditions. Compare against baseline and investigate excessive filesystem scans, repeated full JSON parsing, main-thread I/O, and notification churn. Do not invent performance budgets from nonexistent measurements; set and document acceptance targets with fleet IT.

## 8. Why the design evolved: reconstructed causal chain

**From single notification to repeating reminder:** A standard notification can be missed in a noisy or distracted ambulance workflow. The first app therefore watched SMS/MDT and re-alerted at a short interval. **Evidence:** initial commit subject and README. **Not established:** clinical outcomes or delivery reliability.

**From foreground setup to managed zero-touch:** Crews should not configure helper software, and kiosk hides ordinary app entry points. This drove the invisible bootstrap, Knox auto-run, managed restrictions, and IT-only diagnostics. **Evidence:** bootstrap and UI commits, manifest, README. **Unproven:** every Knox version grants every special permission as expected.

**From a simple timer to alarm-clock scheduling and recovery:** Doze can delay ordinary timers, while process death interrupts in-process scheduling. This drove AlarmManager-based reminders, listener rebind recovery, boot hooks, and a coarse JobScheduler watchdog. **Evidence:** inspected Scheduler/RecoveryJobService code and commit sequence. **Limitation:** watchdog is not a guaranteed one-minute timer; platform constraints remain.

**From notification removal to conservative ACK semantics:** Source apps may cancel notifications without a human seeing them; stale keys and concurrent alerts can produce false acknowledgments. This drove generation-aware ACK markers, serialized state transitions, backup recovery, and partial-ACK tests. **Evidence:** inspected listener/store/policy and numerous commits. **Open policy:** define what counts as operational acknowledgment for TraumaSoft.

**From minimal logs to evidence-grade diagnostics:** When an app appears silent or dead in kiosk, remote IT cannot diagnose it from the crew screen. This drove daily files, device-state diagnostics, streaming ZIP, retention, privacy redaction, and export to Downloads. **Evidence:** EventLog code and successive commits. **Tradeoff:** bounded storage and 14-day retention conflict at high event volume; preserve forensic value with explicit rotation policy.

**From one device class to a Samsung matrix:** The original Android-13+ documentation was expanded to XCover Pro Android-10+ support and A14–A17. **Evidence:** Gradle minSdk 29 and XCover pilot doc. **Open:** actual model/firmware coverage must be measured, not inferred.

## 9. Codex autonomous task queue — seeded, ranked, and nonduplicative

| ID | Priority | Scope | Completion evidence |
|---|---|---|---|
| T-001 | P0 | Reconcile full source tree, current `main`, branch differences, workflows, CI, open issues, release | Machine-readable inventory and SHA ledger |
| T-002 | P0 | Build and run existing unit/lint/static checks; capture failures | Commands, exit codes, logs, environment |
| T-003 | P0 | Trace end-to-end reminder lifecycle and first-repeat/second-repeat continuity | State machine, reproducible test, root cause if broken |
| T-004 | P0 | Audit ACK truth table, generation safety, partial ACK, source removal | Regression matrix and approved conservative behavior |
| T-005 | P0 | Audit reboot/time-change monotonic deadline handling | BootReceiver code review and tests |
| T-006 | P0 | Audit Knox permission/notification effective-state diagnostics | Deploy checklist and evidence collection script |
| T-007 | P1 | Fix loss of earlier daily logs at 3 MiB rotation | Segmented bounded logs and retention/export tests |
| T-008 | P1 | Inspect all unreviewed source, manifest/resources, tests, workflows | Complete source map and risks |
| T-009 | P1 | Reconcile README contradictions and stale deployment instructions | Docs aligned to code and verified OS support |
| T-010 | P1 | Verify APK signing/release provenance and upgrade | CI build, signature inspection, reproducible artifacts |
| T-011 | P1 | Strengthen privacy/security tests for exports and diagnostic entry points | Canary tests, access-control checks |
| T-012 | P1 | Performance baseline and bounded export/queue stress | Recorded metrics and comparison |
| T-013 | P1 | Physical-device Knox pilot protocol and evidence forms | Runnable test plan; mark device tests BLOCKED until run |
| T-014 | P2 | Full Git commit/PR/issue forensic chronology | Every reachable commit mapped to rationale/evidence |
| T-015 | P2 | Synchronize user manual, roadmap, feature status, dossier, release notes | Consistency check and links |

Dependencies: T-001 before substantial edits; T-002 before claiming baseline; T-003/004/005 are distinct and can be independently reviewed; T-007 must not change alarm semantics; T-010 before any production candidate. When blocked by physical-device access, continue T-007 through T-015 rather than asking to stop. Do not execute independent tasks simultaneously against shared files without isolation and merge verification.

## 10. Standing decision policy to avoid repeated questions

Codex is authorized to make ordinary reversible engineering decisions: add tests, refactor with behavior preservation, correct bugs with evidence, improve log safety, optimize memory, update documentation, and commit verified changes within repository protections. It should not ask before every fix or test. It must not change clinical/operational acknowledgment semantics, silently alter default repeat interval, suppress a source category, introduce remote telemetry, expose sensitive data, add paid infrastructure, change signing identity, deploy fleet-wide, or bypass branch protections without explicit approval. If an issue requires such a decision, document a concrete recommended default, impact, alternatives, and precise approval needed, then continue other unblocked tasks.

Preferred technical posture: Kotlin/Android framework APIs; no unnecessary dependencies; no perpetual foreground service or polling loop if event-driven Android mechanisms suffice; no infinite in-memory queue; avoid large buffers, UI startup, wake locks, and unnecessary jobs; make state transitions atomic or safely serialized; fail observably; never auto-ACK because storage or permission checks fail. Respect Android and Knox capabilities instead of inventing bypasses.

## 11. Mandatory permanent engineering record format

For every meaningful change, append a record:

```
Change ID: CHG-YYYYMMDD-NNN
Date/time (UTC):
Starting main SHA / ending SHA:
Related task, issue, PR, release:
Problem statement and observed reproduction:
Operational risk if unfixed:
Evidence links (log, test, diff, device):
Root-cause finding (or explicitly unconfirmed):
Options considered and why rejected:
Decision and rationale:
Changed files and behavior delta:
Backward compatibility / managed-config impact:
Security, privacy, performance and battery impact:
Test commands, environment, exact results:
Tests NOT RUN and why:
Rollback plan and version/signing implications:
Documentation updated:
Remaining uncertainty and next action:
Reviewer / independent verification:
```

Store task ledger in `docs/TASK_LEDGER.md`, chronological change records in `docs/CHANGE_HISTORY.md`, design decisions in `docs/DECISIONS.md`, evidence index in `docs/VERIFICATION_EVIDENCE.md`, deployment in `docs/KNOX_OPERATIONS.md`, and living dossier in `docs/MASTER_ENGINEERING_DOSSIER.md` unless equivalent established files already exist. Do not duplicate authoritative ledgers; reconcile existing files first. Each document needs 'last verified at SHA' and a clear owner. Update README, user manual, roadmap, and release notes when behavior changes. Require doc links to resolve and version numbers to match build metadata.

## 12. Every-session resume snapshot — mandatory closing page

```
PROJECT: DevTunaSoft/missed-alert
MISSION: Unattended repeat alerts for MDT/SMS/missed calls on Knox-managed Samsung phones
UTC TIME / SESSION ID:
MAIN SHA / WORK BRANCH / DIRTY FILES:
CURRENT APK VERSION / SIGNATURE / LATEST VERIFIED BUILD:
OPEN PRS / ISSUES / ACTIVE AGENTS:
LAST COMPLETED TASKS WITH COMMIT LINKS:
CURRENT P0 FAILURES AND REPRODUCTIONS:
EXACT COMMANDS RUN / RESULTS / LOG LINKS:
PHYSICAL DEVICE TEST STATUS (PASS/FAIL/BLOCKED/NOT RUN):
UNRESOLVED DESIGN DECISIONS / APPROVAL REQUIRED:
NEXT THREE UNBLOCKED TASKS, IN ORDER:
EXACT FIRST COMMAND / FILE TO OPEN NEXT SESSION:
DOCUMENTATION FILES UPDATED:
ROLLBACK POINT:
```

If Codex stops midtask, the next session must read this snapshot, inspect GitHub reality, and resume from the last verified checkpoint. Never assume a planned edit was committed. Never rerun a destructive or publishing action without checking whether it already completed. Never call an issue 'fixed' based only on its title or on a green job that omitted device acceptance.

## 13. Acceptance and release gates

**Gate A — source integrity:** clean build, tests, lint/static analysis, no secrets, manifest permissions reviewed, no unresolved P0 correctness issue. **Gate B — deterministic behavior:** notification source filtering, queue persistence, concurrent alerts, repeat continuity, ACK semantics, fault injection, reboot/time changes, diagnostics and export tests. **Gate C — packaging:** versionCode progression, correct package name, stable signing identity, install/upgrade/rollback strategy, APK checksum and reproducibility. **Gate D — managed pilot:** effective Knox permissions and kiosk restrictions, screen-off and Doze, audible repeated alerts, no unintended crew UI, IT log retrieval without ADB, battery impact. **Gate E — fleet readiness:** documented acceptance results for the intended device/OS/Knox matrix, operations training, rollback, ownership, and release authorization. A green CI job can satisfy part of Gates A/B/C; it cannot satisfy D/E.

When reporting status, show gates separately with `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`, or `INCONCLUSIVE`, linked evidence, and next owner. 'Production ready' is prohibited while D/E are unverified.

## 14. Known unresolved factual questions Codex should answer itself first

1. Is `main` at the observed latest commit, and is `pilot/xcover-pro-reminder-recovery` fully integrated?
2. What are all workflows and latest green/red runs, and what do they actually execute?
3. What does `BootReceiver` do with elapsed-realtime deadlines after reboot?
4. How do `AckActivity` and `AckReceiver` handle a source app that cannot open?
5. Does a source app canceling a notification leave a reminder forever, and is that operationally desired?
6. Does rotating today's log overwrite earlier evidence, and what exact tests cover it?
7. Can IT export ZIP through the actual Knox Remote Support workflow with kiosk restrictions?
8. Does the managed config default launch show a screen when Knox auto-runs the app?
9. Are alarm-clock permissions effective across Android 10–17 variants in fleet use?
10. Are signing secrets and release artifacts configured and verified, or only described?
11. Are the README's minSdk and acknowledgment descriptions stale?
12. What are the actual memory, CPU, battery, storage, and wakeup measurements?
13. Does the notification source category/locale handling detect missed calls without false positives?
14. Are the notification channel and device sound routes audible during kiosk/Doze?
15. Which existing test cases assert true repeat continuity, not just initial scheduling?

These questions are not invitations to interrupt the user. They are the incoming engineer's initial investigation checklist. Only a genuinely consequential policy decision or unavailable external resource should be escalated.

## 15. Immediate Codex takeover directive — paste with this book

> You are the lead autonomous engineer for `DevTunaSoft/missed-alert`. Read this entire engineering history book before modifying code. Its operational mission, safety priorities, historical rationale, and standing decisions are authoritative requirements, but its implementation claims must be reconciled with live repository state. Audit `main`, all relevant branches, source, tests, CI, issues, PRs, and releases; build a source-to-requirement map; run the baseline checks; and then implement the highest-priority unblocked fixes. Focus first on the end-to-end repeat chain, acknowledgment safety, reboot recovery, and diagnostic retention. Do not ask for routine permission. Make small, clean, resource-efficient changes with regression tests, independent review, and evidence. Preserve the original zero-crew Knox operation, no-ADB remote export, and low memory/battery requirements. Keep a cumulative, historically accurate engineering ledger explaining every change and why. Update this book, the manual, roadmap, and handoff at every milestone. Do not claim device validation or production readiness without real Samsung/Knox evidence. Continue until a genuine blocker or execution limit; leave an exact resume snapshot for the next session.

**Document-control note:** This book is intentionally substantial, but correctness is more important than its page count. The reconstructed chronology above is based on retrieved commit *subjects* and observed source; full diff-by-diff archaeology remains an explicit Codex task. No new build or device acceptance has been executed by preparing Edition IV.


# PART IV — CODEX ZERO-BABYSITTING TAKEOVER OPERATIONS MANUAL

**Status:** An actionable operating procedure, not evidence of tests run. **Authority:** existing project goals and explicit owner constraints, subordinate to repository truth and applicable platform limitations. **Last prepared:** 2026-11-12. **Baseline observed:** `main` commit `fe19287723293c017ed9fa595863908941edf47d` from the repository commit listing; Codex must confirm current HEAD before work. **Deployment target:** `DevTunaSoft/missed-alert`, package `com.crewalert.missedalert`.

## T0 — Read this before reading the rest of the book

This section is deliberately written as the **first page Codex should read**. The rest of this book supplies rationale, historical context, design contracts, and test catalogs. It does not override the actual checked-out repository. Treat a claim as verified only when you can link it to a source file, commit diff, CI result, or device evidence. Never conflate an engineering plan with a completed implementation.

**The operating mission:** Make the Android Missed Alert app reliably notice eligible notifications from Google Messages, Samsung Messages, TraumaSoft MDT, and missed calls; persist pending state; repeat until a defined acknowledgment; work silently in Knox multi-app kiosk; expose privacy-conscious, exportable diagnostics; minimize CPU, RAM, storage and battery; and pass a reproducible acceptance process. The user expects zero routine crew involvement and minimal owner babysitting. Do not require ADB for normal fleet operations. Never assert that Knox can silently grant a permission without validating the actual device policy and effective state.

**Nonnegotiable boundaries:** No hidden privileged bypasses; no fabricated hardware tests; no silent data loss; no changes to unrelated MMT repositories; no replacement of the user's existing signing identity; no force-push to main; no new persistent development branch unless required by repository rules; no automatic production fleet deployment. Do not request confirmation for routine code, test, documentation, or CI improvements. Request input only for a real policy decision that cannot be safely derived, credentials/access, or production-impacting approval.

**Critical unresolved semantic issue:** Current code's `NotificationRemovalPolicy` acknowledges certain user-originated removal reasons, while source-app removals are retained. README language in older portions describes any removal as acknowledgment. Preserve the current safer behavior until requirements and tests are reconciled; never silently treat app cancellation as crew confirmation. A crew reading an MDT dispatch may not cause its notification to disappear, and a source app may clear it without human action. The app cannot prove a human actually saw an alert; distinguish notification posted, reminder posted, audible observed, and user acknowledgment.

**Do not waste cycles on more speculative research:** Start with repo state, tests, and reproducible failure evidence. Research only when a specific platform, Knox, API, or build problem needs an authoritative answer. The priority is working software.

## T1 — Exact first 60 minutes of a Codex session

1. Confirm GitHub repository and current default branch. Record HEAD SHA, branch protections, open PRs/issues, latest workflows, latest release and artifact availability. Do not trust the SHA in this document as current.
2. Read `README.md`, this dossier, `docs/XCOVER_KNOX_PILOT.md`, build files, Android manifest, workflow YAML, tests and every production source file. Build a **file inventory** with purpose and test coverage.
3. Run `git status --short`, `git log -n 30 --date=iso --stat`, `git branch -a`, and inspect workflow state. Capture outputs to the evidence ledger. If checkout is unavailable, state blocked and use GitHub connector data, not imagined shell output.
4. Run baseline tests and static checks with the repository's actual Gradle wrapper and configured SDK. If the wrapper is absent, investigate before assuming a globally installed Gradle. Capture exact command, tool versions, exit status, stdout/stderr, and artifacts.
5. Trace a single eligible notification through listener, store, scheduler, receiver, notifier, acknowledgment, recovery, diagnostics. Write a compact sequence diagram and identify every point that could fail silently.
6. Compare README/Knox guidance against actual manifest, managed configuration resource, API behavior, source code, and current Android/Knox documentation. Mark mismatches with file and line references.
7. Establish a task ledger: one row per independently verifiable fix, with priority, root-cause hypothesis, affected files, acceptance criteria, tests, evidence, owner/agent, status, commit SHA, and rollback.
8. Select the highest-priority reproducible reliability defect. Implement the smallest fix. Add regression tests that would have failed before the fix. Run the relevant suite, review the diff, and update documentation.
9. If real Samsung hardware is unavailable, continue all automatable tasks, but mark hardware acceptance BLOCKED/NOT RUN; do not repeatedly ask the owner for hardware until a consolidated pilot handoff is ready.
10. End with a self-contained RESUME.md checkpoint, so the next Codex session can start without rereading the entire conversation.

**First-session output contract:** `HEAD`, baseline test result, top five actual risks, one completed verified task or a precise blocker, changed files, test evidence, updated task ledger, and next executable action. If none completed, explain exactly why and what is already prepared to unblock it.

## T2 — Ground-truth snapshot and what is *not* proven

The following were directly read from `main` via GitHub on 2026-11-12; they are not a substitute for a fresh checkout. `app/build.gradle.kts`: AGP 8.13.2, Kotlin 2.3.10, compile/target SDK 36, min SDK 29, versionCode 7, versionName `1.2.4-pilot`, Java 17, JUnit and Robolectric. `AndroidManifest.xml`: notification listener, bootstrap activity, hidden IT diagnostics guarded by `android.permission.DUMP`, recovery job, boot receiver, alarm receiver, acknowledgment receiver. `Config.kt`: default 60-second interval, minimum 15 seconds, optional maximum repeats, managed `extra_packages`, and headless launch configuration. `Scheduler.kt`: alarm-clock scheduling when exact alarms allowed, inexact fallback, persisted next deadline in elapsed realtime, and recovery. `ReAlertReceiver.kt`: logs alarm firing, attempts notification, rearms in finally. `AlertStore.kt`: SharedPreferences JSON with backup, acknowledgment markers and commits. `Notifier.kt`: high-importance reminder channel, alternating IDs, notification-based sound. `RecoveryJobService.kt`: 15-minute periodic watchdog and passive diagnostics. `EventLog.kt`: 14-day daily logs, 3 MiB daily size bound, 50 MiB total, streaming ZIP. `MissedAlertListener.kt`: notification tracking, sync, and removal reasons.

**Not verified here:** successful current Gradle build, exact workflow result, physical reminder cadence, Knox policy effectiveness, source app package names on real devices, audible volume with screen off, release APK signature consistency, memory/battery benchmarks, 24-hour idle test, 14-day log preservation under high event rate, or full repository commit diffs. These must remain NOT RUN/UNVERIFIED until evidenced.

**Potential issue worth targeted investigation:** `EventLog.write()` rewrites the current daily file to a rotation marker after exceeding 3 MiB. This bounds growth but can erase earlier same-day events. Decide whether daily-size retention should be segmented, then test memory, disk, and export behavior. **Potential issue:** `AlertStore.nextAt` uses elapsed realtime, which resets after reboot; verify BootReceiver explicitly invalidates/reconciles it before relying on recovery. **Potential issue:** `Notifier.show` logs `reminder_posted` after `notify()` even when notifications are disabled; distinguish API submission from audible delivery. **Potential issue:** exact-alarm permission checks and `setAlarmClock` semantics require device-specific validation. These are audit hypotheses, not established defects.

## T3 — The decision-rights charter: work without asking

Codex is authorized to inspect, write code, refactor narrowly, add tests, improve diagnostics, update docs, adjust CI, fix build errors, and commit verified changes. Prefer small reversible commits and the repository's designated main integration target. When branch protection requires PRs, use temporary PR branches and merge through required checks; do not bypass protections. Use an isolated working tree for parallel agents, never simultaneous edits to the same file. Codex may choose implementation details when the acceptance contract is unchanged. Codex must not change what counts as acknowledgment, the 60-second default, retention minimum, release signing identity, privacy posture, or production rollout policy without recording the proposed change and reason; ask only if the change materially changes operator expectations or cannot be resolved from documented requirements.

If a decision is uncertain, choose the least destructive reversible option, record assumption + risk, write a test, and continue. If uncertainty could cause missed clinical/dispatch notifications, stop the affected release path, not unrelated development. A blocker must name the exact missing permission, API, hardware observation, credential, or policy and the smallest user action required. Bundle questions into one consolidated request rather than serial interruptions.

## T4 — Prioritized executable work queue

| ID | Priority | Work item | Evidence of completion | Blocker/guardrail |
|---|---|---|---|---|
| MA-001 | P0 | Establish reproducible baseline and current source inventory | Exact commit, commands, test reports, file map | Never claim unrun checks passed |
| MA-002 | P0 | Reconcile acknowledgment semantics and user-removal reasons | Table of reasons, Robolectric tests, README aligned | Do not infer crew viewed MDT |
| MA-003 | P0 | Verify alarm continuity after bursts and partial acknowledgment | Deterministic timing tests, no deadline drift | Avoid duplicate alarms |
| MA-004 | P0 | Verify reboot and package-update recovery | BootReceiver tests and on-device checklist | Elapsed clock resets on reboot |
| MA-005 | P0 | Validate disabled listener/POST_NOTIFICATIONS/channel failure paths | Visible IT blocker, event sequence, no false PASS | Knox policy may not take effect |
| MA-006 | P0 | Preserve privacy-safe forensic evidence under high log volume | Rotation, export, retention, corruption tests | Bounded disk and RAM |
| MA-007 | P0 | Confirm release signing and upgrade continuity | Signature inspection, versionCode monotonicity, signed artifact checks | Never generate replacement fleet key |
| MA-008 | P1 | Simulate concurrency and notification generation reuse | Stress tests with race and stale callbacks | Avoid state corruption |
| MA-009 | P1 | Strengthen watchdog and listener reconnect telemetry | Recovery trace and fault injection | Jobs are not minute-accurate |
| MA-010 | P1 | Document actual Knox managed policy and effective settings | IT playbook + pilot evidence fields | No ADB as fleet dependency |
| MA-011 | P1 | Validate SMS/MDT/missed-call filters | Synthetic and device-specific scenarios | Package names must be discovered |
| MA-012 | P1 | Profile idle CPU, RSS/PSS, wakeups, battery, log I/O | Device measurements with baseline | No fabricated benchmarks |
| MA-013 | P1 | Produce daily ZIP export and IT recovery walkthrough | Export integrity tests, IT instructions | Never log message bodies |
| MA-014 | P1 | Reconcile all project documentation | Contradiction register, updated docs | No rewriting history as fact |
| MA-015 | P2 | Improve developer ergonomics and test automation | One-command CI and diagnostic scripts | No unnecessary dependencies |
| MA-016 | P0 gate | Execute Samsung/Knox pilot acceptance | Signed build, device matrix, timestamped evidence | Requires actual managed devices |

**Scheduling:** Complete independent source/test tasks in parallel only where isolation exists; serialize shared state and integration. When a task fails, write the root cause and add a regression before moving on. Don't mark MA-016 complete from emulator-only evidence.

## T5 — Architecture invariants to preserve

- Every eligible alert is represented by a durable identity and notification generation; a stale callback cannot acknowledge a newer generation.
- Source-app cancellation is not evidence of crew acknowledgment. User removal reasons require explicit, tested interpretation.
- A notification update or burst must not postpone the already scheduled reminder indefinitely.
- Partial acknowledgment must not cancel reminders for unrelated pending alerts.
- At most one effective reminder alarm should be scheduled for the pending queue; recovery should be idempotent.
- A failure to post a notification must not sever the future scheduling chain; a storage read failure must not be silently treated as an empty queue.
- A 15-minute watchdog is recovery assistance, not proof of 60-second cadence.
- User-facing crew UI must not appear on Knox kiosk auto-run; IT status must remain accessible through authorized workflows.
- Every important state transition has a privacy-safe diagnostic event and a clear pass/fail/inconclusive interpretation.
- Memory, disk, wakeups and logging are bounded. No long-running polling loop just to watch notifications.
- No app can guarantee sound under muted/DND, disabled notifications, inaccessible listener, force-stop, platform restrictions, or incompatible audio routing. Detect and report these states honestly.

## T6 — Full alert lifecycle and forensic trace requirements

Expected trace: `listener_connected` → `alert_tracked` → `alarm_scheduled` → `alarm_fired` → `reminder_shown` → `reminder_posted` → next `alarm_scheduled` → repeat → explicit acknowledgment → `alarm_cancelled` when zero pending. A trace may legitimately omit listener connection when previously bound; infer only from timestamps and state. For each stage record wall timestamp with offset, monotonic elapsed time, package identifier, channel identifier, notification generation fingerprint without sensitive content, pending count, repeat index, expected due time, actual fired time, permission state, and outcome. If logging new fields, protect privacy and bound writes.

Distinguish these outcomes: DETECTED, TRACKED, SCHEDULED, FIRED, API_POSTED, CHANNEL_ENABLED, AUDIBLE_OBSERVED, USER_ACKNOWLEDGED, CLEARED, UNKNOWN. `NotificationManager.notify()` returning without throwing does not establish audible playback or human acknowledgment. For a suspected missed alert, reconstruct timeline and identify the first missing stage. Never erase pending state to make a test green.

## T7 — The reproducibility standard for every fix

Every meaningful change gets an issue/task ID, reproducible pre-fix failure, hypothesis, smallest diff, regression test, test command, expected and actual result, environment versions, commit SHA, reviewer, documentation update, and rollback instructions. When a test is nondeterministic, fix the test harness or state the limitation; do not repeatedly rerun until green and conceal earlier failures. CI should publish logs and reports within GitHub storage limits; avoid accumulating large artifacts indefinitely. Include deterministic seeds for fuzz runs and bound their runtime.

## T8 — End-of-session and crash-resume handoff contract

Before every planned stop, create/update `docs/CODEX_RESUME.md` with: repository/branch/HEAD; active task ID; last clean commit; changed and uncommitted files; exact tests executed and results; failing tests and root-cause hypotheses; device evidence; blockers; security/permission risks; next 3 commands; next 3 tasks; rollback; pending approvals; and links to evidence. Update `docs/ENGINEERING_CHANGELOG.md` with *why*, not only *what*. Maintain `docs/DECISIONS.md`, `docs/VERIFICATION_LEDGER.md`, `docs/REQUIREMENTS_TRACEABILITY.md`, and this book; avoid duplicate contradictory sources by cross-linking and assigning one authoritative home for each fact. If a process is interrupted, the next session first reads `CODEX_RESUME.md`, checks git state, verifies whether prior commands committed, and resumes idempotently.

A completed task is not a completed release. Each status line must be exactly one of PASS (evidence attached), FAIL (reproducible evidence), BLOCKED (specific external dependency), NOT RUN (not attempted), or INCONCLUSIVE (insufficient evidence). Never replace these with vague green checkmarks.

## T9 — Escalation packet for unavoidable owner action

Only request the owner to act after collecting all available information. Ask once with: the blocked task, why it matters, what Codex already tried, what access or decision is required, a simple numbered instruction for Knox/GitHub/phone, what the user should send back, and what coding will continue meanwhile. For pilot device tests, provide a printable one-page test sheet and ZIP export procedure. Do not ask the user to use ADB as a routine deployment requirement. Do not ask for signing passwords in chat. Do not change production device policies without explicit authorization.

## T10 — Codex's first message to itself

> I am taking over `DevTunaSoft/missed-alert`. I will verify the current repository state, inspect the complete implementation and tests, establish an evidence-backed task ledger, fix the highest-risk reliability issue, run available automated checks, document every decision and regression, and leave a complete resumption checkpoint. I will not repeatedly ask for permission for routine development. I will not claim hardware or Knox success without real device evidence. I will preserve the existing app, signing identity, main integration target, zero-touch design, privacy boundaries, and low-resource constraints.

---

## T11 — Detailed root-cause playbook: reminder sounds once then stops

Collect a single trace with source package, channel, listener permission, tracked count, nextAt, alarm scheduling mode, `alarm_fired` count, `reminder_posted` count, notification channel importance and sound, device DND, notification cooldown, battery exemption, kiosk policy, screen state, and elapsed time. Do not start by changing the repeat interval.

If no `alert_tracked`, check listener access and source package identity, app notification settings, category filters, group summary exclusion, and whether source posted a notification at all. If tracked but no `alarm_scheduled`, inspect store exceptions, max-repeats, state serialization, and scheduling permissions. If scheduled but no fired event, inspect pending intent, elapsed clock across boot, alarm manager mode, force-stop, and power management.

If fired but no `reminder_posted`, inspect exception logging, channel creation, notification permission, and receiver process lifetime. If posted but no audible sound, inspect channel sound/importance, device volume, DND, mute-all-sounds, separate app sound, Bluetooth routing, Android cooldown and kiosk notification bar restrictions. If repeat stops early, inspect acknowledgment callbacks, notification key reuse, source app cancellations, and whether a newer notification postponed or replaced the deadline.

Build a regression at the first missing stage. Then test one pending item, two independent items, notification bursts, notification updates, screen-off, and simulated process recreation. Preserve a sample anonymized log showing the fixed chain and the exact elapsed intervals. Physical audio acceptance remains separate.

## T12 — Detailed root-cause playbook: reminders never stop

First distinguish intentional source-app notification removal from explicit crew action. A source app may dismiss a notification after its own workflow; current design conservatively retains alerts on app-originated cancellation. Determine whether the app can observe a reliable user action or whether the IT-managed acknowledgment control is required.

Inspect `AckActivity`, `AckReceiver`, `NotificationRemovalPolicy`, `AlertStore.acknowledgeIfMatches`, `AlertStore.acknowledgeAll`, and the notification PendingIntent identities. Verify tap acknowledges the intended generation only; ACK ALL clears all; swipe semantics match product contract; and a stale tap cannot clear a new notification reusing the same key.

Test acknowledgments before first reminder, during alarm firing, after update, after process restart, and during two-alert races. Acknowledgment must be durable before cancellation. If storage commit fails, do not report success or clear the alarm. Record actual before/after pending counts and scheduled state.

## T13 — Detailed root-cause playbook: app appears to be not running

Differentiate app process absence, listener unbound, notification access disabled, Knox force-stop, package not launched after install, receiver not receiving broadcasts, battery optimization, and headless launcher behavior. An invisible app can be functioning correctly without a persistent process or UI; lack of an app window is not failure.

Inspect `BootstrapActivity` launch path, manifest exported components, managed `headless_on_launch`, `RecoveryJobService` scheduling, listener access and reboot events. Verify policy **effective state** on the device, not just configuration assigned in Knox Manage. Review any `knox_auto_run_bootstrap`, `watchdog_scheduled`, `listener_connected`, `self_test`, and blocker events.

When Knox blocks a capability, produce a policy-level remediation with exact package/component identifiers and required permission, and verify on a pilot phone. Do not implement hidden UI hacks, accessibility-service misuse, privileged system-app impersonation, or undocumented Android bypasses.

## T14 — Storage and logging durability protocol

Generate enough synthetic diagnostic events to cross 3 MiB within one day, cross 50 MiB aggregate, span 15 calendar dates, and cross a daylight-saving/timezone boundary. Check that retention keeps required days under realistic volume, and that bounded disk policy does not silently erase the only record of an incident.

Use segmented daily logs if necessary, e.g., `events-YYYY-MM-DD-0001.log` with bounded segments, explicit rotation markers, chronological ZIP export, and a manifest of gaps or truncations. Do not adopt a format change without migration and export compatibility tests. Streaming ZIP should avoid loading all logs into RAM. Test interrupted export and partial file cleanup.

Log timestamps with timezone offsets and monotonic values, event name, low-risk package/channel identifiers, status codes and exception class. Do not log message text, titles, phone numbers, contact names, authentication tokens, or credentials. Provide IT-accessible status and an export path compatible with Knox Remote Support, validated without ADB.

## T15 — Android lifecycle, alarm and power verification protocol

Test screen on/off, unlocked/locked, 30-minute stationary idle, prolonged Doze, battery saver, unplugged, reboot, app update, listener disconnect/reconnect, system time change, timezone change, and process kill. Separate process death from force-stop; Android restricts force-stopped apps and they may need managed-device recovery.

Check scheduled alarm due times against monotonic clock, and ensure stale persisted elapsed deadlines are invalidated after boot. Distinguish exact alarm permission, `setAlarmClock` visibility side effects, fallback mode, and notification sound channel behavior. If an inexact fallback is used, report that minute-level guarantees cannot be made.

Measure actual delay distribution over multiple cycles; record p50/p95/max latency, missed cycles, and reasons. Do not invent thresholds beyond explicit product requirements; propose acceptance limits for owner approval only if a consequential operational decision is needed. No perpetual foreground service or high-frequency polling without quantified justification.

## T16 — Source notification classification and privacy

Confirm actual package names and channels on each supported Samsung/MDT version using privacy-safe discovery. Default watched packages include `com.google.android.apps.messaging`, `com.samsung.android.messaging`, `com.android.mms`, `com.traumasoft.mdt`, and `com.traumasoft.mdtv2`; managed extras extend the set. These are candidate identifiers until verified on the fleet.

Handle group summaries, ongoing notifications, silent notifications, notification updates, duplicate keys, rapid bursts, and concurrent alerts. Missed-call detection must not mistake incoming, ongoing, or rejected calls for missed calls. Tests should include false-positive and false-negative controls, not only positive examples.

Never require the user to provide private SMS or patient content. Use synthetic notifications and sanitized package/channel/timestamp evidence. Confirm no message text leaks through logs, status screens, exports, crash reports, or test artifacts.

## T17 — Device acceptance worksheet and operational go/no-go

Create a matrix for XCover Pro and Samsung A14/A15/A16/A17 with actual model, Android version, One UI, Knox Manage/KSP versions, build hash, APK version/signature, policy ID, SIM/network state, and notification source versions. Do not assume every combination is available; record NOT RUN.

For each available device: verify listener permission, posting permission, channel sound/importance, alarm access, battery allowlist, force-stop policy, kiosk background allowlist, auto-run, and effective policy. Then run five or more consecutive audible reminders for SMS, MDT and missed call while unlocked, locked and idle; test individual and all acknowledgment, reboot, app update, multi-alert, and log ZIP retrieval.

Record observed times, audible yes/no, screenshot/video where appropriate without private content, exported ZIP checksum, and device/operator identifier. A production go decision requires explicit accountable owner approval and documented residual risk. CI green alone cannot authorize deployment.

## T18 — Release engineering, rollback and signing

Inspect existing GitHub Actions and release tags before editing them. Validate build tools and Gradle wrapper, test reports, lint, manifest, target SDK, min SDK, artifact names, package ID, and signing mode. Check that production release APK is signed by the **same authorized key** as the installed fleet version; never rotate the key accidentally.

Keep versionCode monotonic. Verify upgrade-in-place preserves pending state and config where appropriate, and that rollbacks are feasible given Android versionCode and managed-device policy. If a rollback requires uninstall/reinstall, explicitly disclose that it may erase local pending alerts and logs; preserve diagnostics first.

Use main as the integration target while respecting protections. Make one verified change set per commit, and never publish a release or modify production Knox assignments without explicit approval. Retain reproducible build metadata and checksum, but avoid GitHub Actions artifact quota growth.

## T19 — Security, least privilege, and diagnostics access

Review exported activities, broadcast receivers, notification listener, PendingIntent mutability, FileProvider or storage exports, managed restrictions, and diagnostic authorization. Ensure IT-only diagnostics are not accidentally exposed to ordinary kiosk apps or crews. Test component access from an unprivileged caller where practical.

Document permissions actually requested versus those needed, and why. Minimize data collection. Treat ZIP exports as potentially sensitive operational telemetry even when content is redacted; protect retention, access and transfer. Do not add cloud telemetry or third-party SDKs without a requirement and privacy review.

Keep all testing self-contained and deterministic; never access real dispatch content unless an authorized test specifically requires it. If a security issue is found, stop release of the affected build and write a minimally reproducible security test.

## T20 — Low-resource engineering budget and instrumentation

The product should be event-driven: notification listener callbacks, alarms only while pending, coarse recovery job, bounded persistent state, and batched/limited logging. Avoid polling loops, large resident caches, unnecessary services, heavy dependencies, and loading complete log history for export.

Collect baseline idle PSS/RSS, CPU use, wakeups, disk writes/day, APK size, ZIP export peak memory, alarm accuracy, and battery drain on representative phones. Report tool, sampling period, build hash, screen state, and confidence limitations. If no device is available, add instrumentation and mark metrics NOT RUN.

Before accepting an optimization, demonstrate functional equivalence and compare before/after measurements. Do not trade reliability or forensic evidence for an unmeasured micro-optimization.

## T21 — Historical archaeology and decision provenance

For each of the 89 commits surfaced in the 2026-11-12 repository search, inspect commit body and actual changed files/diffs, group into a chronology, and link each significant change to a problem statement, observable effect, tests and subsequent fixes. Commit subjects alone are not proof of the precise implementation or motive.

Create a `docs/HISTORY_INDEX.md` with dated milestones and links to commits, issues, PRs, workflows and releases. For each design decision, distinguish documented user requirement, code observation, inferred rationale, and unknown. Never invent historical author intent. Where a later change reverses an earlier decision, explicitly record the reversal and why.

Keep a `docs/FAILED_APPROACHES.md` describing known failures and rejected techniques, including whether they were attempted or only evaluated. Record how future engineers avoid repeating them. The engineering book should explain the evolution of repeat interval, Doze strategy, Knox bootstrap, acknowledgment, logging retention, ZIP export, signing and XCover support.

## T22 — Acceptance traceability and coverage audit

For every requirement assign a stable ID, owner, severity, source of requirement, implementing files, unit tests, integration tests, device acceptance case, evidence link, and status. Missing code or tests are gaps; missing device evidence is NOT RUN, not PASS.

Generate a report listing uncovered requirements, flaky tests, and all unverified high-severity assumptions. Avoid 120 superficially different tests that share the same untested assertion. Prefer fewer independently discriminating cases over repeated assertions.

At the end of each work cycle, recalculate coverage and show progress as verified gates completed rather than lines of code or word count. Keep this matrix synchronized with the roadmap and end-user manual.

## T23 — Multi-agent coordination without conflicting edits

One lead agent owns the task ledger and integration. Independent reviewers may inspect architecture, tests, security, and Knox documentation in parallel. Only one agent writes a given source file at a time; use isolated worktrees or PR branches when available.

Assign each task a unique scope, affected files, acceptance criteria, and integration dependency. Review the diff independently, run targeted tests, and integrate through required checks. When two agents discover the same defect, consolidate into one task rather than duplicate patches.

Never leave unreviewed code on main, never discard another agent’s uncommitted work, and never merge a green CI run whose test suite did not exercise the changed behavior.

## T24 — Human-friendly IT manual and crew impact

Write IT instructions in simple numbered steps for Knox Manage, KSP, pilot installation, permissions verification, diagnostics dashboard access, ZIP export through Remote Support, update, and rollback. Include screenshots or exact UI labels only when validated against the actual current Knox interface.

Explain to operations what the app can and cannot prove: notification detection and reminder posting are observable; human reading/understanding is not. Document how an alert is acknowledged, when it repeats, how multiple pending items behave, and what happens if a source app removes its notification.

Keep the crew workflow zero-touch for setup and maintenance. Any proposed crew action for routine deployment violates the established product goal; an explicit acknowledgment interaction is permitted only if consistent with the operational design.

## T25 — Final takeover exit criteria

Codex has reconstructed the repository, established one authoritative task ledger, fixed all reproducible P0 software defects, added regression coverage, and produced verifiable build/test artifacts. Documentation is synchronized, release signing and upgrade paths are checked, and the next session can resume from `CODEX_RESUME.md` without needing chat history.

All physical-device, audio, and Knox pilot gates are either PASS with evidence or clearly BLOCKED/NOT RUN with one consolidated owner action packet. No unknown state is labeled success. Production rollout is explicitly separate from code completion.

The final report provides exact HEAD SHA, released version if any, tasks completed, tests run, performance results, device matrix, unresolved risks, artifacts, and next action. If no tool can continue work after the session, say so; do not promise indefinite background activity.

## T26 — Copy-paste instruction for the incoming Codex session

**Repository:** `https://github.com/DevTunaSoft/missed-alert`

**Directive:** Read PART IV of this dossier first, then the entire engineering history book. You are the lead engineer. You have standing authority to implement routine scoped changes, run tests, repair failures, and maintain the repository documentation. Begin with the exact T1 checklist and MA-001. Reconstruct Git history and current branch truth, do not assume this dossier is current. Use the T4 work queue, preserve T5 invariants, and leave a T8 resumption checkpoint. Work through successive verified cycles until blocked by real external requirements. Do not repeatedly ask the owner for routine decisions. Do not claim tests, APK builds, physical audio, or Knox behavior without actual evidence. Do not deploy to the production fleet without explicit approval.

**Deliverable format after each cycle:** `COMPLETED` with evidence; `FAILED` with root cause and regression; `BLOCKED` with exact action; `NEXT` with immediate executable steps. Record commit SHA and test output paths. Keep code clean, modular, memory-efficient, battery-efficient and security-conscious. Respect repository protections and integrate verified work into main.