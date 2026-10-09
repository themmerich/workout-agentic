# Habit tracker — design

Status: approved in brainstorming on 2026-10-09. Replaces the placeholder start page.

## Goal

The start page lets a user track their own habits: create any number of habits, group them into
categories (or create a category first and add habits to it), tick them off per day, and see how
they did over the last days and weeks. Examples: take a multivitamin every day, go running twice a
week, eat more than 20 g of fibre every day.

Out of scope for now: a dashboard with heatmaps and rates (planned later, built on the same
backend report), quantities with units, monthly targets, fixed weekdays, manual sort order.

## Decisions

| Topic           | Decision                                                                                                 |
| --------------- | -------------------------------------------------------------------------------------------------------- |
| Ownership       | Every user owns their categories, habits and checks. Nobody else sees them, admins included.            |
| Recording       | A habit is yes/no per day. A target such as "over 20 g" is part of the name.                             |
| Frequency       | `DAILY`, or `WEEKLY` with a target of N days (1–7) per calendar week, on any days.                       |
| Back-filling    | Any past day and today can be checked and unchecked; future days cannot.                                 |
| Layout          | A week grid (mockup variant A): habits grouped by category, the 7 days of a calendar week as columns.   |
| Management      | On the same page: buttons above the grid, dialogs, a menu per row and per category heading.             |
| Ending a habit  | Archive (hidden, history kept) or delete (gone with its checks).                                         |
| Category        | Optional. Habits without one are listed under "Ohne Kategorie" at the end.                               |
| Computation     | The backend stores checks and computes week progress and streaks.                                         |

## Rules

- **Week**: calendar week, Monday to Sunday. "Today" is decided in the server's zone, as on the AI
  cost page.
- **Week progress**: checked days of the week against the target. For `DAILY` the target is the
  number of days of that week up to and including today (a Thursday of the current week: 4; a
  past week: 7; a future week: 0). For `WEEKLY` the target is N.
- **Streak, daily habit**: consecutive checked days ending today. If today is not checked yet, the
  streak counts up to yesterday, so it does not drop to 0 in the morning.
- **Streak, weekly habit**: consecutive calendar weeks whose target was reached. The current week
  counts only once its target is reached; otherwise the streak counts up to last week.
- **Order**: categories alphabetically, habits alphabetically within their category, "Ohne
  Kategorie" last.
- **Deleting a category** keeps its habits; they move to "Ohne Kategorie".
- **Archived habits** are left out of the week grid and listed in an archive dialog, from where
  they can be restored or deleted for good. Their checks stay.
- **Access**: a user reaches only their own data. Another user's habit or category answers 404, not
  403, so the answer does not say that it exists. A super-user without an open tenant is turned
  away by the existing tenant checks.

## Backend

New package `de.prime_ux.backend.habits`, following `branches` and `aiusage`.

### Schema — `V2__create_habits.sql`

- `habit_categories`: `id`, `user_id` (FK users, `ON DELETE CASCADE`), `name`, `created_at`;
  unique `(user_id, LOWER(name))`.
- `habits`: `id`, `user_id` (FK users, `ON DELETE CASCADE`), `category_id` (FK habit_categories,
  `ON DELETE SET NULL`), `name`, `frequency` (`DAILY`/`WEEKLY`), `weekly_target`, `archived_at`,
  `created_at`; `CHECK` that `weekly_target` is between 1 and 7 for `WEEKLY` and null for
  `DAILY`.
- `habit_checks`: `habit_id` (FK habits, `ON DELETE CASCADE`), `day` (`DATE`); primary key
  `(habit_id, day)`.

The user's tenant cascades to the user, so deleting a tenant takes all of it along.

### API

The user always comes from `CurrentSession`, never from the request.

| Method & path                                    | Purpose                                                                                                   |
| ------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| `GET /api/habits/week?start=YYYY-MM-DD`          | The grid for one calendar week (see below). Without `start` the current week; any day is moved to its Monday. |
| `PUT /api/habits/{id}/checks/{day}`              | Check a day. Idempotent. A future day answers 400.                                                        |
| `DELETE /api/habits/{id}/checks/{day}`           | Uncheck a day. Idempotent.                                                                                |
| `POST /api/habits`                               | Create a habit: `name`, `categoryId` (optional), `frequency`, `weeklyTarget` (for `WEEKLY`).              |
| `PUT /api/habits/{id}`                           | Change a habit (same fields).                                                                             |
| `DELETE /api/habits/{id}`                        | Delete a habit with its checks.                                                                           |
| `PUT /api/habits/{id}/archived`                  | `{ "archived": true \| false }` — archive or restore.                                                     |
| `GET /api/habits/archived`                       | The archived habits.                                                                                      |
| `GET /api/habit-categories`                      | The user's categories.                                                                                    |
| `POST /api/habit-categories`                     | Create a category: `name`. A name already taken answers 409.                                              |
| `PUT /api/habit-categories/{id}`                 | Rename. A name already taken answers 409.                                                                 |
| `DELETE /api/habit-categories/{id}`              | Delete; its habits lose the category.                                                                     |

Validation: a blank name, `WEEKLY` without a target, or a target outside 1–7 answer 400.

Week response:

```json
{
  "start": "2026-10-05",
  "today": "2026-10-09",
  "categories": [{ "id": "…", "name": "Ernährung" }],
  "habits": [
    {
      "id": "…",
      "name": "Multivitamin",
      "categoryId": "…",
      "frequency": "DAILY",
      "weeklyTarget": null,
      "checkedDays": ["2026-10-05", "2026-10-06"],
      "weekDone": 2,
      "weekTarget": 5,
      "streak": 2
    }
  ]
}
```

`streak` counts days for a daily habit and weeks for a weekly one.

### Computation — `HabitWeekReport`

A pure class: habits, their checks, today and the week's Monday in, the week response out. No
clock, no database, so the rules above are pinned down by unit tests. The controller reads all
checks of the user's active habits up to today in one query; with one row per habit and day that
is a few thousand small rows at most, and the query can be bounded later without changing the API.
The later dashboard builds on the same class.

### Security

`/api/habits/**` and `/api/habit-categories/**` are open to every signed-in user; the existing
`anyRequest().authenticated()` rule covers them, and `CurrentSession.tenant()` turns away a
super-user without an open tenant.

## Frontend

The placeholder domain `home` is replaced by a new domain `domains/habits`; the route `/` shows
the week grid. The sidebar entry "Start" becomes "Gewohnheiten" with a check icon.

- `model/habit.ts` — the API types.
- `model/week.ts` — framework-free helpers: the Monday of a week, the ISO week number, the 7 days
  of a week, previous and next week, whether a day lies in the future.
- `data/habits-service.ts` — an `httpResource` for the displayed week, and methods to check and
  uncheck, to create, change and delete habits and categories, and to archive. After every change
  the week reloads, so progress and streak are the server's.
- `ui/habit-week-table` — presentational: category headings, one row per habit with 7 day cells,
  week progress and streak; today highlighted, future days dashed and disabled; menus emit events.
- `feat-week/habits-page` — header with "KW 41 · 5.–11. Okt.", ‹ › and "Heute"; buttons "Neue
  Gewohnheit" and "Neue Kategorie"; the grid; dialogs for a habit (name, category or none,
  "täglich" or "N× pro Woche") and for a category (name); a confirmation before deleting; an
  "Archiv" button opening the archived habits with restore and delete. The displayed week is kept
  in the URL (`/?week=2026-10-05`).

Behaviour:

- A click on a cell flips it at once and then sends the request; on an error the cell flips back
  and a toast says so.
- Without habits the page shows an empty state with a hint and "Erste Gewohnheit anlegen".
- On narrow screens the table scrolls horizontally; the name column stays in place.
- Each cell is a `button` with `aria-pressed` and a label such as "Multivitamin, Donnerstag,
  9. Oktober"; fully keyboard-operable with a visible focus ring.
- Texts in German and English in `public/i18n`.

Errors: a failed load replaces the grid with an error message; a failed save raises a toast and
reverts the cell; a 409 on a category name is shown in the dialog as "Name schon vergeben".

## Tests

Backend:

- `HabitWeekReportTest` (plain unit test, fixed dates): week progress for daily and weekly habits,
  mid-week and in past and future weeks; daily streak with today checked and still open; weekly
  streak with the current week's target reached and not yet; a gap breaking a streak; a start day
  that is not a Monday.
- `HabitControllerTest` and `HabitCategoryControllerTest` (MockMvc + Testcontainers): create,
  change, delete, archive and restore; check and uncheck, idempotent; a future day answers 400;
  deleting a category leaves its habits without one; a duplicate category name answers 409;
  another user's or another tenant's habit answers 404; a super-user without a tenant answers 403;
  validation answers 400.

Frontend:

- Unit tests (Vitest): `week.ts` including the year boundary (ISO week 1 of 2027 starts on
  4 January); the table (today highlighted, future days disabled, aria labels); the page (toggle
  with revert on error, week navigation, dialogs, empty state).
- E2E `habits.spec.ts` with a mocked API: create a habit, check a day, change the week.
- The `home` specs go; e2e specs that wait for the "Willkommen" heading wait for "Gewohnheiten".
