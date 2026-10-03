# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/) (while pre-1.0, minor bumps can include breaking
schema changes — the app wipes and reseeds local data on any such bump, per the versioning note
below).

## [Unreleased]

## [0.8.2] - 2026-10-02

### Added
- A notification after any app update finishes installing ("Household Tracker updated — tap to
  open the new version"). Android replaces the APK on disk but doesn't reliably kill a backgrounded
  process right away, so switching back to the app after an update could resume the *old* running
  process with the old code still loaded — looking like the update silently did nothing unless you
  knew to force-stop first. Listens for the system's `MY_PACKAGE_REPLACED` broadcast and opens a
  fresh `MainActivity` when tapped, which guarantees new code loads. Verified on-device: reinstalling
  over a running instance triggers the broadcast, starts a new process, and posts the notification
  with a working tap target.

## [0.8.1] - 2026-10-02

### Fixed
- The self-updater always downloaded to the same fixed file path. If that file was still there
  from a previous update (updating twice in a row, which is exactly what testing v0.7.0 then
  v0.8.0 back-to-back did), DownloadManager could leave it untouched instead of overwriting it —
  the new APK silently landed somewhere else and the installer reinstalled the stale one. The
  system install flow completes normally either way, so this looked like a successful update while
  the app stayed on the old version. Now deletes any leftover file before starting each download.

## [0.8.0] - 2026-10-02

### Added
- "Push all data to this household" button on the Admin tab. Joining a household code only starts
  a listener on that code's Firestore path — it never retroactively sent a device's existing local
  data anywhere, so data created before a join (or before household codes existed at all) was
  invisible to anyone else under that code. This pushes every row currently in Room once, which is
  what actually gets two devices to converge after a join.

## [0.7.0] - 2026-10-02

### Fixed
- **Security:** every install of the app previously synced through a single hardcoded Firestore
  path shared by all users, combined with anonymous auth — meaning anyone who installed the app
  could read and write every household's data. Each install now generates its own short household
  code on first launch (shown on the Admin tab, with a "Join a different household" option to
  switch to someone else's code), and every synced collection is scoped under
  `households/<code>/...`. This is a shared-secret style boundary, not full per-user
  authentication — anyone who has the code can still join that household, which is the point: it's
  meant to be shared between your own devices.
- The GitHub repo was private, so the in-app update checker's unauthenticated API call always got
  HTTP 404 regardless of whether a release existed. Made the repo public (no secrets were ever
  committed — signing keys and `google-services.json` are injected by CI from GitHub secrets) and
  gave the update checker a clearer message instead of a raw status code.

## [0.6.1] - 2026-10-02

### Fixed
- Completing one of the 12 starter chores (or logging a starter family/For Us activity) didn't
  sync to other devices, even though manually-added chores did. Cause: each device seeds its own
  starter data independently with a random id, so "Clean counters" on one device and "Clean
  counters" on another were different chores under the hood — a completion record pointing at
  one device's chore id simply didn't exist on another device, and was silently dropped rather
  than crash. Seeded rows now get a deterministic id derived from their title, so every device
  converges on the same id for the same starter item with no network coordination needed.

## [0.6.0] - 2026-10-02

### Added
- A real release pipeline: a signed, repeatable `./gradlew assembleRelease`, a GitHub Actions
  workflow that builds and attaches a signed APK to a GitHub Release on every version tag push,
  and `tag-release.sh` to trigger it. See `RELEASING.md` for the one-time secret setup and the
  cut-a-release flow.
- An in-app self-updater (no Play Store, so no Play In-App Update API): Admin · Stats has a
  manual "Check for updates" section that polls the GitHub Releases API, downloads the signed
  APK via `DownloadManager`, and hands it to the system installer — which still requires your
  explicit confirmation, same as any sideloaded install.

## [0.5.1] - 2026-10-01

### Changed
- ChorePhoto sync (Firebase Storage + Firestore) was built, verified working end to end on real
  devices, then **reverted**: as of late 2024, Firebase requires the paid Blaze plan (a billing
  account on file) to create a Storage bucket at all, even for usage that would stay entirely
  within its free tier. That's a real, if small, departure from "this stays free," so chore
  reference photos are a **permanent**, deliberate exception to the sync rollout rather than a
  pending TODO — see the doc comment on `ChorePhoto` in `Entities.kt`. Every other entity
  (Assignees, Chores, Family/Parental Activities, chore completions, subtasks + checks, activity
  ideas, both activity logs) still syncs via the free Spark-plan Firestore/Auth setup from 0.2.0.

## [0.5.0] - 2026-10-01

### Added
- Cross-device sync extended to the remaining local-only tables: chore completions (per-day),
  chore subtasks and their per-assignee check state, activity ideas, and both Family/Parental
  Activity weekly completion logs. ChorePhoto is the one deliberate holdout — its reference
  photos are local files and would need a real upload pipeline (Firebase Storage) to sync
  meaningfully, which is out of scope here.

### Fixed
- A chore/activity completion synced from another device could crash the app on pull if its
  parent (chore/subtask/activity) hadn't synced locally yet — most commonly, completing one of
  the seeded starter chores, since seeded data was never pushed to Firestore. Now skipped with a
  warning log instead of crashing; the row is lost rather than retried, an accepted gap of this
  sync pass.

### Added
- Cross-device sync extended to For Us / Parental Activities (same pattern as Assignees/Chores/
  Family Activities). Weekly completion logs are still local-only. Every synced entity now uses
  a genuine UUID id end to end — the PlanEntry id-conversion workaround from 0.2.0/0.3.0 is gone.

## [0.3.0] - 2026-10-01

### Added
- Cross-device sync extended to Family Activities (same pattern as Assignees/Chores). Activity
  ideas and completion logs are still local-only.

## [0.2.1] - 2026-10-01

### Fixed
- Weekly and 2x/week chores were hardcoded due on Monday (and Monday+Thursday), with no way to
  change it. A chore added on any other day had no valid due date until its first real cycle date,
  so it silently never showed up anywhere — it was saved, just invisible. The Add/Edit dialog now
  has a day-of-week picker (defaulting new chores to today), and card labels show the picked day,
  e.g. "Weekly · Thu".

## [0.2.0] - 2026-10-01

### Added
- Cross-device data sync, proof of concept: Assignees and Chores now sync across devices via
  Firebase Firestore (requires your own Firebase project — see README). Every other entity
  (activities, completions, photos, subtasks) is still local-only; the same pattern can be rolled
  out to each in turn.
- Date navigation (`< Prev` / `Today` / `Next >`) on the Mapper tab, so a day's roadmap can be
  planned ahead instead of only "today."
- A dedicated "💞 Intimate" filter on the For Us tab, replacing the old Admin-wide toggle —
  intimate suggestions are now opt-in per viewing session rather than a global on/off switch.
- Support for older tablets: minSdk lowered from 26 to 24 via Android's core library desugaring
  (backports `java.time`, which is all that actually required API 26).

### Fixed
- Audience/budget/category/slot option pickers across For Us and Activities now visually show
  the selected choice (were using a chip style with no selected-state affordance).

## [0.1.0] - 2026-09

Initial build-out: chores with frequency/priority/time-window/subtasks/reference photos, family
activities with time-of-day slots and creative-activity idea prompts, For Us weekly suggestions
(personal/together/adult-only/family, budget-filtered), the Day Roadmap (Mapper) tab for
sequencing a day's tasks, a Scheduled tab for planned-ahead For Us activities, an Admin/Stats tab
with monthly completion stats and an assignee editor, light/dark/system theming, and daily chore
reminders. All data local-only (Room/SQLite), no sync.

[Unreleased]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.6.1...HEAD
[0.6.1]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/releases/tag/v0.1.0
