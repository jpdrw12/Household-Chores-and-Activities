# Household Tracker

Native Android app (Kotlin + Jetpack Compose + Room) for family chores and activities:

- **Chores** — add chores, set daily/weekly/2x-weekly frequency, pick a due date, assign to
  a family member (defaults to "Family"), check them off, and expand a photo section per
  chore showing what "done" should look like. Seeded with starter chores on first launch.
- **Family Activities** — indoor/outdoor activities with daily checkboxes, grouped by
  time of day (start-up, mid-play, wind-down, bedtime) with the current slot highlighted
  as "Suggested now".
- **For the Parents** — weekly personal / together / adult-only activity suggestions,
  filterable by low/medium/high budget.
- **Admin · Stats** — a separate tab (not part of daily flow) showing monthly chore
  completion rate and activity counts, all computed from local data.

All data is stored locally in a Room/SQLite database on-device. No account, no server,
no cloud sync.

## Getting started

Open this folder in Android Studio (Koala or newer) — it will offer to generate the
Gradle wrapper JAR on first sync. Minimum SDK 26, target/compile SDK 34.

```bash
./gradlew assembleDebug   # after Android Studio has generated the wrapper jar
```

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

## Ideas not yet built

- Reminder time (currently fixed at 6pm) isn't user-configurable yet.
- No per-photo full-screen viewer — thumbnails only.
- No undo after deleting a chore/activity.
