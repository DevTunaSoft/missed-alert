# 05 — Codex Autonomous Takeover, Development, and Resumption

## Standing authorization
Codex may inspect, plan, implement, refactor, test, diagnose, fix, document, and advance established work without repeatedly requesting approval. It must respect security controls, protected branches, signing keys, and the existing app mission. Ask only for genuine missing access, consequential changes to behavior/security, or actions that require approval.

## First 60 minutes
1. Verify repository default branch, HEAD SHA, recent commits, open PRs/issues, workflows, release artifacts, signing configuration, and current docs.
2. Read `docs/MASTER_ENGINEERING_DOSSIER.md` and this engineering folder in full.
3. Build a source-to-requirement map and identify conflicts between code and documentation.
4. Run existing tests/build/lint if the environment supports them. Record commands, output, SHA, and any blockers.
5. Inspect alarm and acknowledgment state transitions for the highest-severity failure modes.
6. Choose one unblocked P0 defect or missing regression; implement the smallest sound change.
7. Rerun focused and broader tests, review for privacy, concurrency, battery and memory impact.
8. Commit verified changes to the designated integration branch, respecting branch protections.
9. Update cumulative dossier, change ledger, verification matrix, and resume checkpoint.
10. Continue the next work item until execution limits or a genuine blocker.

## Work queue and priority
P0: Repeat continuity, explicit acknowledgment correctness, source cancellation semantics, listener access/recovery, boot/Doze alarm behavior, kiosk zero-touch, audibility diagnostics, logging integrity and privacy.
P1: End-to-end automated tests, reproducible Knox pilot, signing/release/rollback, performance and battery baselines, user/IT manual.
P2: UX improvements only when they do not compromise background reliability or crew invisibility.

## Mandatory change record
For each work item record: ID, requirement, defect reproduction, starting SHA, files changed, technical reason, alternatives rejected, test names/commands/results, regression coverage, risk, rollback, ending SHA, and next steps. Never write 'tested' without actual test output.

## Resume contract
Keep `docs/CODEX_RESUME.md` updated at every stable checkpoint with current HEAD, task ID, worktree status, last completed commands, results, uncommitted changes, unresolved errors, next exact command, and required human intervention. A new Codex session should read it before acting and reconcile with live repository state.

## Multi-agent coordination
When parallel agents are available, partition work into non-overlapping file ownership and independent tests. Do not allow concurrent edits to shared state/store/scheduler files without explicit integration order. One integrator reconciles tests and merges verified changes. Avoid creating conflicting persistent branches.

## Final response contract
Report DONE, IN PROGRESS, BLOCKED, NOT RUN. Include actual commit SHAs, test evidence, known risks, and precise next action. No claim of background continuation after the session ends.

## Start prompt
> Read the master engineering dossier and every file in docs/engineering. Verify the live repo and CI. Reconstruct the full historical engineering book from commit diffs and evidence. Maintain the documentation and a durable resume checkpoint. Begin with the highest-priority repeat/ACK/recovery defect, implement and test it, and continue autonomously without repeatedly requesting routine permission.
