# OpenGroove

Native Android (`app/`) and iOS (`iosApp/`) share domain/playback policy in
`shared/`. For shared product behavior, use `docs/feature-parity.md`; `README.md`
explains provider and playback boundaries, and `PRIVACY.md` covers data handling.

## Product constraints

- Play media in-app only through provider/publisher URLs intended for playback.
  Commercial music platforms use their official app/web experience.
- Preserve the visible official YouTube player or browser handoff. Do not add
  extraction, hidden playback, downloads or an offline audio cache.
- Podcast discovery metadata is not audio permission: use publisher RSS/Atom
  enclosures for episodes. Background refresh is metadata-only and inexact.
- Keep provider/source/license information visible. Directory availability does
  not prove a programme is live; language matching can be heuristic.
- Keep EN, 简中 and 繁中 UI choices distinct from content-language filters and
  preserve both platform contracts when changing shared behavior.
- Credentials stay in untracked local configuration; signing assets, personal
  listening data and third-party test keys do not belong in source or reports.

## Completion checks

For a focused change, run affected unit checks and inspect the changed UI/player
flow. `scripts/verify-all.sh android|ios|all` provides platform gates when that
scope warrants them. The iOS gate runs XcodeGen and can update the generated
project; preserve existing edits and inspect its diff. Documentation-only edits
need content/link checks, not both platform builds.

Source/tests, simulator, installed app, real provider playback and background
behavior are separate evidence. A feature fix includes local check/repair work;
device installation, production signing, store release, push or merge require
authority for that operation. Do not silently change provider/account setup.
