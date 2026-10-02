# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/) (while pre-1.0, minor bumps can include breaking
schema changes — the app wipes and reseeds local data on any such bump, per the versioning note
below).

## [Unreleased]

## [0.4.0] - 2026-10-01

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

[Unreleased]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/jpdrw12/Household-Chores-and-Activities/releases/tag/v0.1.0
