# Household Tracker

Native Android app (Kotlin + Jetpack Compose + Room) for family chores and activities:

- **Chores** — add chores, set daily/weekly/2x-weekly/custom-interval frequency and a
  Low/Normal/High/Critical priority, pick a due date, assign to a family member (defaults to
  "Family"), check them off, and expand a photo section per chore showing what "done" should
  look like. A chore that's missed on its scheduled day keeps showing every day after
  (marked **OVERDUE**) until it's checked off, instead of disappearing until its next cycle.
  The list sorts overdue items and Critical/High priority chores to the top. Chores can also
  break down into **subtasks** (e.g. "Put clothes away" → Shirts / Pants / Socks / Outerwear),
  each checked off per-assignee per-day, so more than one person can confirm their own share
  of a shared chore. Seeded with starter chores (and starter subtasks for "Put clothes away")
  on first launch.
- **Family Activities** — indoor/outdoor activities with daily checkboxes, grouped by
  time of day (start-up, mid-play, wind-down, bedtime) with the current slot highlighted
  as "Suggested now".
- **For the Parents** — weekly personal / together / adult-only activity suggestions,
  filterable by low/medium/high budget. An admin-gated "Spicy" category (solo and together
  intimate suggestions) stays hidden until switched on in Admin · Stats — off by default.
- **Admin · Stats** — a separate tab (not part of daily flow) showing monthly chore
  completion rate and activity counts, all computed from local data, plus the appearance
  theme toggle and the spicy-content toggle.

All data is stored locally in a Room/SQLite database on-device. No account, no server,
no cloud sync.

## Getting started

Open this folder in Android Studio (Koala or newer), or build from the command line —
the Gradle wrapper is checked in and works standalone:

```bash
./gradlew assembleDebug
```

Minimum SDK 26, target/compile SDK 34, JDK 17.

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

## Since v0.1

- Daily reminder notification (6pm, via WorkManager) listing any chores still unchecked
  for the day. Requests `POST_NOTIFICATIONS` on first launch (Android 13+).
- Chores support multiple reference photos (picked from the device gallery), shown as a
  thumbnail strip in the collapsible photo section, each removable individually.
- Admin · Stats now breaks chore completion down by assignee for the current month.
- Chores and both activity lists support edit and delete from the list UI (pencil/trash
  icons), not just add.
- Chore frequency adds a "Custom" option: due every N days from creation date, for
  schedules that don't fit daily/weekly/2x-weekly.
- Admin · Stats has a Light/Dark/System appearance toggle, stored via DataStore, applied
  app-wide independent of the OS setting.
- Chores carry a priority (Low/Normal/High/Critical) and now persist as **overdue** once
  missed, rather than only appearing on their single scheduled day; overdue and
  High/Critical chores sort to the top of the list.
- Admin · Stats gained a "Show spicy activities" toggle (off by default) gating a new
  intimate-suggestion category in "For Us", seeded with a few tasteful starter ideas under
  Personal and Together.

## Ideas not yet built

- Reminder time (currently fixed at 6pm) isn't user-configurable yet.
- No per-photo full-screen viewer — thumbnails only.
- No undo after deleting a chore/activity/subtask.
- Facial recognition for auto-selecting who's checking off a task, with a manual picker
  fallback for shared tasks — flagged as a separate future project, meaningfully larger
  scope than anything else here (on-device ML Kit/CameraX + a per-family-member enrollment
  flow).
