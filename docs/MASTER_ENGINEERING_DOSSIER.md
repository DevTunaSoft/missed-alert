# MMT Missed Alert — Master Engineering Handoff and Codex Takeover
**Repository:** DevTunaSoft/missed-alert  
**Document status:** Repository-native operational handoff. This is NOT the full 38,247-word historical book previously generated outside GitHub. Codex must expand it into a verified, cumulative engineering history using repository evidence.  
**Baseline observed:** main at `fe19287723293c017ed9fa595863908941edf47d` on 2026-11-12. Re-verify on takeover.

## 1. Why this application exists
MMT operates Samsung Android phones managed through Samsung Knox in a multi-app kiosk environment. Crews must not miss operationally important notifications from TraumaSoft MDT, Google/Samsung/AOSP messaging, or missed-call notifications. The core operational failure reported during development was that reminders might sound once and fail to repeat, or that the application appeared inactive, including outside kiosk. The application is designed to repeat reminders until explicit acknowledgment, survive normal Android lifecycle disruptions, and provide IT with evidence of what happened. The phone is NOT the MDT: TraumaSoft MDT is a separate monitored application.

This is a zero-crew-configuration system. Knox administrators deploy and configure it. The crew must not open or maintain the app, grant permissions manually, export logs, or interact with an app dashboard. IT may use Knox Remote Support to view device state and export daily logs without ADB. The app should remain hidden from the crew kiosk while operating in the background. Never claim absolute delivery guarantees that Android cannot provide.

## 2. Non-negotiable engineering contract
- Clean, modular, readable Kotlin; minimal memory, CPU, battery, storage, and wakeups.
- Correctness and durable acknowledgment state over superficial 'green' tests.
- Repeat every 60 seconds by default while unacknowledged, subject to actual Android platform capabilities. Default maximum repeats: 0 (unlimited).
- Monitor `com.traumasoft.mdt`, `com.traumasoft.mdtv2`, `com.google.android.apps.messaging`, `com.samsung.android.messaging`, `com.android.mms`, and properly classified missed calls. Additional packages may be supplied via managed configuration.
- Distinguish source-app cancellation from explicit user acknowledgment. Do not silently erase a real pending alert when its source notification disappears.
- Preserve pending state and recovery across process death, listener reconnect, reboot, and app update as far as platform APIs permit.
- Log richly but do not log SMS bodies, names, phone numbers, secrets, or other notification content.
- Maintain daily, bounded, remotely exportable logs for at least one week (current implementation targets 14 days).
- Autonomous tests, fault injection, root-cause fixes, regression tests, and documented evidence. Never call a test PASS without having run it.
- Keep documentation synchronized: README, roadmap, user/IT manual, feature status, engineering dossier, and resumption ledger.
- Prefer verified integration to main, with rollback and repository protections respected; avoid proliferating long-lived branches.
- Standing authorization to inspect, implement, test, debug, document, and advance roadmap work. Ask only about genuine blockers, external permissions, high-impact scope changes, or security approvals.

## 3. Evidence and limitations
This handoff derives from selected inspected repository files and 89 commit records observed through GitHub search on 2026-11-12. Commit messages provide clues to intent, not proof that behavior works. A successful CI build does not prove notification audibility on a Samsung phone under Doze and Knox. The entire tree, every diff, latest CI result, release signing, and physical device acceptance have NOT been independently verified as part of this handoff. Codex must re-inspect current reality.

Observed build configuration: applicationId/namespace `com.crewalert.missedalert`, versionCode 7, versionName `1.2.4-pilot`, minSdk 29, targetSdk/compileSdk 36, AGP 8.13.2, Kotlin 2.3.10, JUnit 4.13.2, Robolectric 4.16.1. README's older Android minimum may conflict with current Gradle: correct docs from source of truth.

## 4. Observed architecture and WHY
**MissedAlertListener.kt:** Android NotificationListenerService monitors allowed sources and resynchronizes active notifications after reconnect. Reason: app must see source notifications without user action and recover from listener disconnect. Validate missed-call classification, package variants, listener permission state, repost handling, generation-aware removal, and concurrent events.

**AlertStore.kt:** SharedPreferences JSON stores pending items and acknowledgment markers; commits and backups are used for durability. Reason: process death cannot be interpreted as acknowledgment. Observed generation-aware acknowledgment and bounded marker history. Audit malformed-but-parseable records, commit failures, stale callbacks, corruption recovery, backup atomicity, counter resets, and consistency under concurrency. Note that current add writes both items and backup to the same new snapshot: this is not an independent previous-generation backup.

**Scheduler.kt:** Single self-rearming AlarmManager alarm. Uses setAlarmClock when exact-alarm access is available; setAndAllowWhileIdle fallback otherwise. Reason: periodic WorkManager/JobScheduler is not a 60-second reliable reminder clock. Audit Doze behavior, permission denial, user-visible alarm-clock side effects, elapsed vs wall-clock deadline, reboot, clock changes, alarm replacement, stale PendingIntent, and 60-second delivery evidence. Observed nextAt uses elapsedRealtime persisted in preferences: reboot resets elapsedRealtime; audit boot rearming and any stale-deadline implications.

**ReAlertReceiver.kt:** Loads pending items, posts reminder, increments repeat counter, reschedules in finally, and ensures recovery watchdog. Reason: one notification failure must not break the entire chain. Audit repeat-limit exits, synchronous exception handling, persistent counter failure, notification-post failures, and races with acknowledgment.

**Notifier.kt:** High-importance reminder channel `realert_v2`, default notification sound, vibration, alternating notification IDs, tap-to-review and ACKNOWLEDGE ALL. Reason: each repeat must actually alert instead of silently updating a notification. Audit channel mutability, notification permission, disabled app notifications, DND, muted channel, audio stream, alternating ID cancellation, sound on every repeat, and source-app launch/ACK semantics. 'notify() returned' is not proof a human heard sound.

**RecoveryJobService.kt:** Persisted periodic JobScheduler watchdog (~15-minute minimum) inspects listener, notification, channel, exact alarm, battery, and pending-alarm health, requests listener rebind, and recovers missing alarms. Reason: an independent recovery path is necessary. This job is coarse and can be delayed by Doze; it cannot restart a force-stopped app or silently grant restricted permissions.

**Config.kt:** Managed restrictions supply extra_packages, headless_on_launch, interval_seconds (default 60, minimum 15), max_repeats (default 0). Reason: fleet control belongs to IT/Knox rather than crew. Verify schema resource, parsing, invalid values, defaults, and policy deployment.

**EventLog.kt:** Fourteen calendar days of daily log files, 3 MiB/day and 50 MiB total budget; wall-clock and monotonic timestamps, streaming ZIP export. Reason: IT needs a flight recorder without unbounded memory or storage. IMPORTANT: observed daily-size handling rewrites the current daily file with a rotation marker when full, which may discard earlier events from that day. Investigate segmented rotation preserving as much history as allowed and export manifest/checksums; do not claim all-day completeness until proven.

**BootstrapActivity, StatusActivity, ItDiagnosticsActivity, AckActivity, AckReceiver, BootReceiver:** Provide silent managed initialization, IT diagnostics, explicit acknowledgment, and boot/update recovery. Verify exported-component permissions and usability through Knox Remote Support.

## 5. Historical engineering narrative (commit subjects, not full diff proof)
The repository's 89 observed commits span 2026-11-05 through 2026-11-12. Reconstruct actual changes from diffs and tests. Significant phases:

**Foundation, Nov 5:** `8ee6de523f6e` introduced repeating unacknowledged MDT/SMS notifications every 60 seconds. `5b8a798daa4a` added missed calls and Knox zero-touch guidance. `21205a2388c2` established a stable latest-release APK. `c6046c78bb43` introduced CI signing support. `a0a156d15413` introduced privacy-safe logging and time-change rearming. `ed34e9c02eaa` added Downloads log export. Why: initial fleet usability, distribution, and IT troubleshooting.

**Reliability hardening, Nov 5:** `618deb19b87c` integrated XCover Pro and alarm recovery; `890df7b7114a` rearmed after alerts, partial ACK, and reboot. `3cb3968bfd75` and `a8cd9ae16794` added large deterministic fuzz models. `944bf6872c2b`, `c595886a10ee`, `9af612bdf9b4`, `007a11e65297`, `e94f49404fed` addressed atomic acknowledgment, backup recovery, invalid records, commit failures, and ACK corruption. Why: silent loss or resurrection of pending alerts is unacceptable.

**Knox and lifecycle, Nov 5–6:** `ea632e41651f` corrected notification dismissal semantics. `f62879b3442c` protected the rescheduling chain. `ba0b1b57cce1` introduced invisible Knox auto-run bootstrap. `51ba864cb4a3` added unattended initialization. `e8d0bd9e95f7` released v1.2.0 pilot. `8aa43c0689b3` addressed APK icon metadata and signing verification. `0f8bf3313409` through `604ed0ba202f` refined manual IT status and silent managed launch. Why: the crew should never have to launch the app or troubleshoot permissions.

**Repeat continuity, Nov 11:** `081cfe34adcc` preserved the existing alarm deadline across bursts of incoming notifications. `a758a06cb39b` preserved the reminder schedule on partial acknowledgment. `7e5a9490f788` versioned repeat-continuity fix. Why: new alerts and partial ACK must not indefinitely postpone or break repeats.

**Diagnostics and recovery, Nov 12:** `d4c24a81f6c7` extended bounded log retention to 14 days. `82628d53ab46`, `ae9a41e948ed` implemented streaming ZIP export. `52617d57f964` isolated IT notification tests from real queue. `8812cc6c4f31` introduced passive watchdog health checks. `30af7c601114` shortened overdue reminder recovery grace. `3c176b608c05`, `22dfc990383e` bounded daily logs. `f93a57ece775`, `db1146ad9ff0`, `9749dcf83ed0`, `64afae3ff8c9` improved permission/channel diagnosis and isolated alarm recovery from diagnostic faults. `0c12777a9283` prevented partial ZIP files in Downloads; `509b7e0d72a3`, `fe1928772329` corrected status newline formatting. Why: autonomous diagnosis and recoverability must be real, not merely described.

For EVERY meaningful commit, Codex must retrieve the diff, classify changed behavior, link the motivating defect/requirement, inspect tests, and document whether the intended fix is proven. Do not infer a successful production result from the commit subject.

## 6. Priority-ordered autonomous work queue
P0-A: Reconcile current branch, HEAD, CI, releases, workflows, open PRs/issues, build scripts, tests, and full source tree. Capture exact SHAs and commands.
P0-B: Audit notification intake -> persisted pending -> scheduled alarm -> repeat -> explicit ACK -> cancel; create traceability matrix and deterministic integration tests.
P0-C: Reproduce the reported 'alerts once but never repeat' and 'app does not run' conditions through fault injection and actual device tests where accessible. Classify permission/Knox restrictions separately from code defects.
P0-D: Audit AlarmManager deadline and boot recovery, 60-second cadence, Doze, exact alarm denial, wall/monotonic time, and user-visible alarm clock consequences.
P0-E: Audit source cancellation vs user dismissal; ensure an unrelated notification's removal cannot clear a newer generation.
P0-F: Verify Knox background operation, auto-run, kiosk visibility, battery optimization exemption, force-stop blocklist, notification listener permission, POST_NOTIFICATIONS, and channel sound. Document which permissions can be managed by Knox versus require explicit provisioning.
P0-G: Verify full diagnostic exports and retention; repair any destructive daily rotation with tests; make errors observable without leaking sensitive content.
P0-H: Run all available JVM, Robolectric, static checks, lint, build, CI, and Android device tests. Separate PASS/FAIL/BLOCKED/NOT RUN.
P1-A: Performance and battery baselines on A14/A15/A16/A17/XCover Pro and relevant Android versions; record actual evidence.
P1-B: Release signing, APK verification, install/update and rollback; ensure same signing identity and monotonic version codes.
P1-C: End-user and IT manuals, Knox Remote Support workflow, release notes, risk register, and final production acceptance report.

## 7. Acceptance criteria and tests
Minimum scenarios: new SMS; Google/Samsung/AOSP package differences; TraumaSoft MDT both package variants; missed call only (not incoming call); notification burst; duplicate notification; reposted same key with newer postTime; source app auto-removal; explicit single ACK; ACK ALL; stale tap; partial ACK; notification swipe; notification access revoked/restored; posting permission revoked/restored; channel disabled/muted; DND; low battery; Doze; screen off; reboot; package update; process kill; force-stop limitation; exact-alarm permission denied; alarm schedule exception; storage corruption; backup corruption; full disk; clock/timezone change; daily rollover; log size cap; ZIP failure; repeated managed auto-run; Knox kiosk hidden state; no ADB remote log export; two-week retention; no sensitive text leakage.

Each test record MUST contain ID, requirement, device/API, initial state, steps, expected behavior, actual behavior, logs/attachments, result, commit SHA, and whether it was simulated or physical. Never silently convert NOT RUN into PASS.

## 8. Knox operational runbook
Before fleet rollout: confirm APK signature and version; deploy through Knox Manage; set managed configuration including headless launch and source packages as needed; enable auto-run after install; ensure background operation is allowed in multi-app kiosk while app UI is hidden; allowlist battery optimizations; block force stop where supported; grant notification listener and notification posting permissions through supported enterprise policy or documented provisioning; verify notification channel sound/importance and device volume; restart and verify. Use Knox Remote Support to inspect IT status and export diagnostic ZIP without ADB. For any permission that Knox cannot grant, record a real deployment blocker rather than claiming an automatic bypass.

Pilot must demonstrate audible repeats over time while locked, under Doze, after reboot, and after notification listener recovery. Collect evidence on at least one representative device per OS/Knox policy family. Escalate platform-imposed restrictions instead of using undocumented APIs.

## 9. Mandatory living records
Maintain these repository files, creating them if absent:
- `docs/MASTER_ENGINEERING_DOSSIER.md`: full cumulative book (this file is its seed).
- `docs/ENGINEERING_CHANGE_LEDGER.md`: chronological per-change record: commit SHA, date, reason, alternatives, implementation, test evidence, regressions, rollback.
- `docs/VERIFICATION_MATRIX.md`: requirement -> source code -> test -> device evidence -> status.
- `docs/KNOWN_ISSUES.md`: reproducer, impact, root cause, current state, owner/next action.
- `docs/CODEX_RESUME.md`: exact HEAD, current task, last commands/results, modified files, blockers, next commands.
- `docs/KNOX_IT_RUNBOOK.md`: policy settings, pilot checklist, remote support log export, recovery and rollback.
Synchronize README, user manual, feature status, and roadmap. Avoid duplicate contradictory 'source of truth' documents: link to the canonical ledger.

## 10. Codex execution protocol
1. Read this entire handoff; inspect actual repo state and history. Do not trust stale prose over code.
2. Establish one task ledger with acceptance criteria, priority, and evidence.
3. Implement the highest-priority unblocked item. Do not ask routine permission.
4. Run focused tests; investigate any failure to root cause; add regression; rerun full relevant suite.
5. Independently review security, resource use, lifecycle, backward compatibility, and fleet operations.
6. Record actual results and evidence. Do not invent device measurements or tests.
7. Commit verified changes to main subject to branch protections; preserve rollback. Avoid permanent duplicate branches.
8. Update living book, change ledger, verification matrix, and resume checkpoint in the same work cycle.
9. Continue until genuinely blocked, execution limits are reached, or established acceptance gates pass.
10. At session end, write a concise operator-facing PASS/FAIL/BLOCKED/NOT RUN report and exact next action. Do not claim work continues after the session ends.

## 11. Decisions Codex may make without asking
Refactor internal modules, add tests, improve diagnostics, fix verified defects, update docs, improve CI, and tune bounded performance without changing core crew behavior. Ask before collecting sensitive message content, weakening security, requiring crew interaction, changing the explicit ACK contract, introducing paid services, replacing the Knox deployment model, or taking an irreversible fleet-wide action.

## 12. First takeover instructions
Start with current repository HEAD and CI. Build an evidence-backed component inventory. Read and diff all 89 historical commits where accessible, recording what changed and why, with uncertainty labels. Run existing test suite. Then investigate the repeat-alarm lifecycle and logging integrity before other feature additions. Expand this handoff into a genuine book with a verified historical narrative, not boilerplate. Preserve the original operational purpose throughout.

**Critical honesty requirement:** This seed handoff is committed through GitHub, but the separately generated long-form dossier has NOT been transferred. Codex should reconstruct and enlarge the repository document from real code, Git history, tests, and available project materials. Never state the long-form book was uploaded unless it actually was.
