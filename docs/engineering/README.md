# MMT Missed Alert — Engineering Library

This folder is the source-controlled engineering documentation library for the MMT Missed Alert Android application. It supports the canonical [Master Engineering Dossier](../MASTER_ENGINEERING_DOSSIER.md).

## Mission
Deliver reliable, repeat-until-acknowledged alerts for TraumaSoft MDT, supported SMS applications, and missed calls on Samsung Knox-managed Android phones, with zero routine crew intervention. Operate in the background of multi-app kiosk, preserve durable acknowledgment state, minimize resource use, and expose robust, privacy-safe IT diagnostics through Knox Remote Support.

## Documents
- [01 — Origin and decision history](01_ORIGIN_AND_DECISIONS.md)
- [02 — Architecture and reliability](02_ARCHITECTURE_AND_RELIABILITY.md)
- [03 — Verification and acceptance](03_VERIFICATION_AND_ACCEPTANCE.md)
- [04 — Knox deployment and operations](04_KNOX_OPERATIONS.md)
- [05 — Codex autonomy and resumption](05_CODEX_OPERATIONS.md)
- [Master dossier](../MASTER_ENGINEERING_DOSSIER.md)

## Evidence status
These documents are engineering handoff records and plans. They are **not** proof of physical-device acceptance. Repository history, current code, CI logs, and real Samsung/Knox testing must be reconciled by the next engineer.

**Important completeness note:** The separate 38,247-word Markdown engineering book created in the prior session has not been transferred to GitHub. This folder and the master handoff are substantial repository-native documentation, but do not represent a verbatim copy of that book. Do not remove this notice until its actual full contents are transferred and verified.

## Maintenance contract
For every meaningful change, update the dossier and corresponding chapter, record the motivating issue, source files, exact commit, tests actually executed, evidence, limitations, and rollback. Keep README, roadmap, feature status, IT manual, and verification matrix consistent. Do not confuse inferred intent from commit subjects with verified results.
