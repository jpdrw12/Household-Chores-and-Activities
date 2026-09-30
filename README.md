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

## Ideas not yet built

- Push notifications / reminders for due chores.
- Multiple photos per chore with a full gallery viewer (currently a placeholder section).
- Per-assignee stats (who did what) on the admin screen.
- Editing/deleting existing chores and activities from the UI (currently add-only; retiring
  a chore is wired in the repository but not exposed in the UI yet).
- Custom recurrence (e.g. "every other Tuesday") beyond daily/weekly/2x-weekly.
- Light/dark theme toggle independent of system setting.
