# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/) (while pre-1.0, minor bumps can include breaking
schema changes — the app wipes and reseeds local data on any such bump, per the versioning note
below).

## [Unreleased]

## [0.14.0] - 2026-10-07

### Added
- Mapper roadmap tasks now have a checkbox — checking one off reads from and writes back to the
  same completion state as the item's own tab (Chores/Activities/For Us), not a separate flag.
- Each roadmap task can be tagged Morning/Afternoon/Night (tap again to clear). Purely a label for
  now — doesn't reorder or group the list.

### Changed
- `reorderPlan` now takes the full reordered task list instead of just type/id pairs, so a drag
  reorder preserves each task's period label instead of wiping it (plan entries used to be deleted
  and fully reinserted on every reorder).

### Internal
- `PlanEntry` gained a nullable `period` column (`DayPeriod`: MORNING/AFTERNOON/NIGHT) — destructive
  schema bump, DB version 16 → 17.


### Changed
- "For Us" tab: Intimate is now a third mutually-exclusive tab alongside Parents and Family & Kids,
  instead of a toggle that layered on top of whichever of those was selected. Selecting it shows
  only intimate activities; Parents/Family & Kids no longer reveal them regardless of the old
  toggle's state. New activities added while on the Intimate tab default to intimate.

## [0.12.2] - 2026-10-03

### Changed
- Overdue section is now collapsible, like Completed and Not scheduled today, and collapsed by
  default.

## [0.12.1] - 2026-10-03

### Fixed
- The new Due/Overdue/Not-scheduled split (0.12.0) had a redundant "or it's the chore's creation
  day" fallback in the "due today" check, meant to keep a freshly-added chore from being invisible
  before its first real cycle date. It was redundant (a chore added via the UI already defaults its
  due day to today's weekday) and actively harmful: a destructive schema migration re-seeds any
  missing starter chore with a fresh `createdAt`, so on migration day *every* seeded chore counted
  as "due today" regardless of its real schedule — which is exactly what made the whole three-way
  split look like it wasn't doing anything. Removed; the Overdue section's own fallback still
  covers the "don't hide a freshly-seeded chore" case.

## [0.12.0] - 2026-10-03

### Changed
- Split the Chores screen's main list into three: **Due today** (today is literally the chore's
  scheduled day), **Overdue** (missed a past scheduled day, still unchecked), and **Not scheduled
  today** (collapsible — today isn't a scheduled day and nothing's pending). Previously a missed
  weekly/custom chore stayed mixed into the main list forever until checked off, which meant e.g. a
  Monday-only chore still showed as "overdue" on a Saturday instead of moving to "Not scheduled
  today" like a same-week chore that's simply not due yet.

## [0.11.0] - 2026-10-03

### Added
- "Not scheduled today" section on the Chores screen, collapsible like Completed — lists active
  chores that simply aren't due today and have no overdue instance pending, so the main list isn't
  the only place to see a chore exists.

## [0.10.0] - 2026-10-03

### Added
- "Weekdays" chore frequency (Mon–Fri), alongside Daily/Weekly/2x-week/Custom.
- Tapping a reference photo thumbnail now opens it full-screen; tap again (or the close button) to
  dismiss. Was thumbnail-only before.

### Fixed
- A synced-in reference photo could silently vanish right after arriving: on a fresh device, its
  Firestore doc and its chore's doc sync down as two independent listeners, so the photo could try
  to insert before its chore existed locally yet, violating the choreId foreign key and getting
  dropped with no retry. Same race `ChoreOccurrenceSync` already handles — caught and skipped the
  same way, which self-heals on the next app start since a new listener registration redelivers
  every doc as if newly added, and by then the chore has synced down.

## [0.9.0] - 2026-10-02

### Added
- Chore reference photos now sync between devices. Each photo is downscaled (max 1024px) and
  JPEG-compressed until it fits as a base64 string inside its own Firestore document, instead of
  uploading to Firebase Storage — Storage now requires the paid Blaze billing plan just to create a
  bucket at all, which didn't fit this project's "stays free" goal (a Storage-backed version was
  built and reverted for exactly this reason; see ChorePhoto's doc comment). A real 1.4MB phone
  photo compresses to ~130KB this way with no visible quality loss, which puts the free Firestore
  tier's 1GiB storage cap at roughly 6,000+ photos kept at once. EXIF orientation is applied before
  compressing so photos taken in portrait don't come out sideways on another device. Verified
  end-to-end: added a photo via the gallery picker, confirmed it landed in Firestore scoped to the
  household's code, and confirmed the round-tripped copy renders correctly in the app.
- "Push all data to this household" (Admin tab) now also pushes existing reference photos.

### Changed
- `ChorePhoto` switched from an auto-incrementing Long id to a client-generated UUID string, same
  as every other synced entity — this is a destructive schema change (bumped DB version 15 → 16),
  so existing reference photos are wiped on upgrade like any other pre-1.0 schema bump.

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
