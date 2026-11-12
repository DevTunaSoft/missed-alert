# Codex resume checkpoint

Updated 2026-11-12 UTC after lifecycle and configuration hardening.

- Branch / current SHA: `main` / `8e68d0d`
- Active task: reconcile authoritative alert acknowledgment semantics and establish the next
  verified engineering cycle.
- Completed: cloned live `main`; inspected dossier, engineering library, source, tests, workflows,
  history, issue #2 and merged PR #1; ran all four repository model/source checks.
- Changes in completed cycles: README and listener documentation now state that source-app
  cancellation does not acknowledge a pending alert; reboot persistence wording matches the
  implementation; README Android/build metadata now matches `minSdk=29`, `targetSdk=36`, and CI's
  Gradle 8.13 / SDK 36 toolchain; listener and boot callbacks isolate store faults and schedule
  watchdog recovery; managed negative `max_repeats` values normalize to unlimited.
- Verification: `python3 tests/source_regression.py` (15 passed); `python3 tests/fuzz_reminder_model.py`
  (500,000 transitions passed); `python3 tests/fuzz_stress_model.py` (2,000,000 transitions and
  1,000 retention cases passed); `python3 tests/persistence_regression.py` (8 contracts passed).
- Not run: Android lint/unit tests; no Gradle wrapper is tracked and `gradle` is unavailable in this
  environment. Physical Samsung, Knox, Doze, audio, and release-signing verification remain not run.
- Blocker: issue #2 requires IT-managed notification listener / POST_NOTIFICATIONS grants and a real
  Samsung/Knox device; source and CI evidence cannot substitute for that acceptance.
- Next exact action: run the repository's Android CI-equivalent `gradle :app:lintDebug
  :app:testDebugUnitTest`, then continue the P0 lifecycle and acknowledgment audit without
  duplicating existing store/scheduler behavior.
