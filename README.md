# Household Tracker

Native Android app (Kotlin + Jetpack Compose + Room) for family chores and activities:

- **Chores** — add chores, set daily/weekly/2x-weekly/custom-interval frequency, a
  Low/Normal/High/Critical priority, and an optional start time + estimated end time, pick a
  due date, assign to a family member (defaults to "Family"), check them off, and expand a
  photo section per chore showing what "done" should look like. A chore that's missed on its
  scheduled day keeps showing every day after (marked **OVERDUE**) until it's checked off,
  instead of disappearing until its next cycle — and if it has an estimated end time, it goes
  overdue the same day once that time passes, even before the date rolls over. The list sorts
  overdue items and Critical/High priority chores to the top. Chores can also break down into
  **subtasks** (e.g. "Put clothes away" → Shirts / Pants / Socks / Outerwear), each checked off
  per-assignee per-day, so more than one person can confirm their own share of a shared chore.
  Seeded with starter chores (and starter subtasks for "Put clothes away") on first launch.
- **Family Activities** — indoor/outdoor activities with daily checkboxes, grouped by
  time of day (start-up, mid-play, wind-down, bedtime) with the current slot highlighted
  as "Suggested now". On weekday mornings, the start-up suggestions narrow to quick, low-prep
  options that fit a tight pre-school window (a full walk or bike ride only surfaces on
  weekends).
- **For Us** — weekly personal / together / adult-only / family activity suggestions,
  filterable by low/medium/high budget, with a date-navigable **Day Roadmap (Mapper)** tab for
  sequencing a day's chores/activities/For Us items, and a **Scheduled** tab for anything given a
  target date. A per-session "💞 Intimate" filter reveals intimate suggestions (off by default).
- **Admin · Stats** — a separate tab (not part of daily flow) showing monthly chore
  completion rate and activity counts, all computed from local data, plus the appearance
  theme toggle and the assignee editor.

All data is stored locally in a Room/SQLite database on-device — no account needed to use the
app. Assignees and chores additionally sync across devices via Firebase Firestore, a proof of
concept (see [Cross-device sync](#cross-device-sync) below); every other entity is still
local-only.

## Getting started

Open this folder in Android Studio (Koala or newer), or build from the command line —
the Gradle wrapper is checked in and works standalone:

```bash
./gradlew assembleDebug
```

Minimum SDK 24, target/compile SDK 34, JDK 17.

## Cross-device sync

Assignees, Chores, Family/Parental Activities, chore completions, chore subtasks + their check
state, activity ideas, and both activity completion logs all sync across devices via Firebase
Firestore; this is a proof of concept, not a finished feature — see `CHANGELOG.md` for the full
list and the known limitations (a single shared, unauthenticated Firestore path — fine for one
private household testing this, not for shipping to strangers).

Chore reference **photos** are the one deliberate, permanent exception — see the doc comment on
`ChorePhoto` in `Entities.kt`. A Firebase Storage-backed version was built and verified working,
then reverted: Storage now requires the paid Blaze plan (a billing account), which breaks this
project's "stays free" goal even though actual usage would likely cost $0.

To build and run with sync working:
1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
   (free Spark plan) and register an Android app with package name `com.jpdrw.household`.
2. Download `google-services.json` and place it at `app/google-services.json` (gitignored —
   per-developer config, never commit a real one).
3. In the Firebase console, enable **Authentication → Anonymous** sign-in and create a
   **Firestore Database** (test mode is fine for a single-household proof of concept).

Without this file, the app still works fully offline — Firebase calls fail silently and the app
falls back to local-only Room storage.

## Project structure

```
app/src/main/java/com/jpdrw/household/
  data/            Room entities, DAOs, AppDatabase, Repository (single data access point)
  ui/
    chores/        Chores tab
    activities/     Family activities tab
    parental/       Parental/couple activities tab
    stats/          Admin stats tab
    theme/          Material3 theme
  HouseholdApp.kt   Application class, owns the database + repository singletons
  MainActivity.kt   Hosts the Compose nav graph
```

## Versioning & changelog

See `CHANGELOG.md` for release history. `./bump-version.sh [patch|minor|major]` bumps
`versionName`/`versionCode` in `app/build.gradle.kts`; add a matching entry to `CHANGELOG.md`
before committing the bump. The app is pre-1.0 — a schema-changing release wipes and reseeds
local on-device data (see the Room migration note in `AppDatabase.kt`).

## Ideas not yet built

- Reminder time (currently fixed at 6pm) isn't user-configurable yet.
- No per-photo full-screen viewer — thumbnails only.
- No undo after deleting a chore/activity/subtask.
- Cross-device sync (see above) uses a single shared, unauthenticated Firestore path rather than
  real per-household accounts. Chore reference photos are a permanent exception, not synced
  (would need a paid Firebase plan).
- Facial recognition for auto-selecting who's checking off a task, with a manual picker
  fallback for shared tasks — flagged as a separate future project, meaningfully larger
  scope than anything else here (on-device ML Kit/CameraX + a per-family-member enrollment
  flow).
