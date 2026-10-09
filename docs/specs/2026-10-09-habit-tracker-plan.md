# Habit tracker — implementation plan

Implements [the design](2026-10-09-habit-tracker-design.md). Work happens on `feature/next01`;
nothing is committed until the maintainer says so. Each step ends green (`./gradlew test` for the
backend steps, `pnpm lint`, `pnpm test`, `pnpm build` for the frontend steps).

## Backend

### 1. Schema

- `backend/src/main/resources/db/migration/V2__create_habits.sql` with `habit_categories`,
  `habits` and `habit_checks` as in the design, including the cascades, the unique name index
  and the frequency/target `CHECK`.
- Index `habits (user_id)`; `habit_checks` is reached through its primary key.

### 2. Entities and repositories (`de.prime_ux.backend.habits`)

- `HabitCategory` (user, name, createdAt; `rename`).
- `HabitFrequency` enum `DAILY`, `WEEKLY`.
- `Habit` (user, category, name, frequency, weeklyTarget, archivedAt, createdAt; `update`,
  `archive(Instant)`, `restore()`, `isArchived()`, `clearCategory()`).
- `HabitCheck` with an embedded id `(habitId, day)`, or a plain repository with native
  `insert … on conflict do nothing` / `delete` — whichever keeps idempotence simplest; the
  embedded id is the default.
- Repositories:
  - `HabitCategoryRepository`: `findAllByUserIdOrderByNameAsc`, `findByIdAndUserId`,
    `existsByUserIdAndNameIgnoreCase`.
  - `HabitRepository`: active habits of a user with their category, archived habits of a user,
    `findByIdAndUserId`, clearing the category of a user's habits before a category is deleted
    is left to `ON DELETE SET NULL`.
  - `HabitCheckRepository`: checks of a set of habits up to a day (one query), exists, delete by
    id.

### 3. `HabitWeekReport` + `HabitWeekReportTest`

- Pure, package-private: `build(categories, habits, checksByHabit, LocalDate weekStart, LocalDate
  today)` → `HabitWeekResponse`.
- Helpers: Monday of a day, week progress, daily and weekly streak, the order (categories by
  name, habits by name, uncategorised last — the response carries the habits in that order).
- Write the test first: every case listed under "Tests" in the design.

### 4. Request/response records

- `HabitWeekResponse` (start, today, categories, habits with checkedDays, weekDone, weekTarget,
  streak), `HabitResponse` (for create/change/archived list), `HabitCategoryResponse`.
- `HabitRequest` (`@NotBlank name`, `categoryId`, `@NotNull frequency`, `weeklyTarget` with a
  record-level check: required and 1–7 for `WEEKLY`, ignored/null for `DAILY`).
- `HabitCategoryRequest` (`@NotBlank name`, trimmed), `ArchiveHabitRequest` (`boolean archived`).

### 5. Controllers

- `HabitController` at `/api/habits`: `GET /week`, `PUT`/`DELETE /{id}/checks/{day}`, `POST`,
  `PUT /{id}`, `DELETE /{id}`, `PUT /{id}/archived`, `GET /archived`.
  - User from `currentSession.user()`, tenant enforced via `currentSession.tenant()` (403 for a
    super-user without one).
  - Foreign or unknown ids → 404; a category id of another user → 404.
  - Future day → 400; "today" from `LocalDate.now(ZoneId.systemDefault())`, as the AI cost page.
- `HabitCategoryController` at `/api/habit-categories`: `GET`, `POST`, `PUT /{id}`,
  `DELETE /{id}`; a taken name → 409 with `ConflictResponse("name")`.
- No change to `SecurityConfig` beyond a comment, if any.

### 6. Controller tests

- `HabitControllerTest`, `HabitCategoryControllerTest` with `@SpringBootTest`, MockMvc,
  Testcontainers and `@AsUser`, cleaning the habit tables first in `@BeforeEach` (and adding the
  habit repositories to the clean-up of the existing tests only if a foreign key requires it —
  the cascades from `users` should make that unnecessary).
- Cases as listed in the design.

### 7. Demo data (dev profile)

- `DemoDataSeeder` gives the demo users `admin` and `user` the example habits (categories
  "Ernährung" and "Sport": Multivitamin daily, ≥ 20 g Ballaststoffe daily, Laufen 2× per week)
  with a few checks over the last two weeks, only when the user has no habits yet. Makes the page
  worth looking at right after `bootRun`.

## Frontend

### 8. Model

- `domains/habits/model/habit.ts`: `HabitFrequency`, `HabitCategory`, `WeekHabit`, `HabitWeek`,
  `HabitInput`, `ArchivedHabit`.
- `domains/habits/model/week.ts` + spec: `mondayOf`, `isoWeek`, `daysOf`, `previousWeek`,
  `nextWeek`, `isFuture`, ISO date (`YYYY-MM-DD`) parsing/formatting without time zone drift.

### 9. Data

- `domains/habits/data/habits-service.ts` (+ spec for the URLs): `week` as `httpResource` over a
  `weekStart` signal; `categories` resource; `check`, `uncheck`, `createHabit`, `updateHabit`,
  `deleteHabit`, `setArchived`, `archived` resource, `createCategory`, `renameCategory`,
  `deleteCategory`; each mutation reloads what it affects.

### 10. UI — `habit-week-table`

- Inputs: the week, the categories, `today`, a set of cells currently pending/flipped. Outputs:
  `toggle(habit, day)`, `editHabit`, `archiveHabit`, `deleteHabit`, `renameCategory`,
  `deleteCategory`.
- Category headings with a menu (PrimeNG `p-menu`), rows with 7 cell buttons (`aria-pressed`,
  label "Name, Wochentag, Datum"), progress "3/5" and streak ("2 Tage" / "5 Wochen"); today
  highlighted, future cells dashed and disabled; sticky name column, horizontal scroll.
- Spec: highlighting, disabled future cells, labels, events.

### 11. Feature — `habits-page`

- `domains/habits/feat-week/habits-page.{ts,html,spec.ts}` and
  `domains/habits/api/habits-routes.ts`.
- Week header with KW and date range, ‹ › and "Heute"; week in the query parameter `week`.
- Optimistic toggle: flip locally, call the service, revert and toast on error.
- Dialogs: habit (signal form: name required, category select with "Ohne Kategorie", frequency
  select-button, target 1–7 only for weekly) and category (name required, 409 shown inline).
- Confirm dialogs before deleting a habit or a category; archive dialog with restore/delete.
- Empty state and load error.
- Spec: toggle and revert, navigation, dialogs, empty state, error.

### 12. Wiring and clean-up

- `app.routes.ts`: `''` loads `habitsRoutes`; remove `domains/home`.
- Sidebar: "Start" → "Gewohnheiten" (`pi pi-check-square`); sidebar spec follows.
- i18n `de.json` / `en.json`: `habits.*`, `shell.habits`; drop `home.*` and `shell.home`.
- Existing e2e specs: wait for the heading "Gewohnheiten" instead of "Willkommen", and mock
  `GET /api/habits/week` and `/api/habit-categories` where the start page is reached.
- New e2e `habits.spec.ts`: create a habit, check a day, change the week.

### 13. Docs

- `ROADMAP.md`: tick "First feature" as the habit tracker, list the dashboard with heatmap as next.

## Verification

- `./gradlew test` in `backend/`; `pnpm lint`, `pnpm format:check`, `pnpm test`, `pnpm build`,
  `CI=1 pnpm e2e` in `frontend/`.
- Manual check in the running app (dev profile, demo data): toggle cells, back-fill a past week,
  create, archive, restore and delete a habit, rename and delete a category.
- Then stop and hand the uncommitted diff over for review.
