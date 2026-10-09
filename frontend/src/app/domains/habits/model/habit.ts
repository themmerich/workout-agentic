/** How often a habit is meant to be done: every day, or a number of days per calendar week. */
export type HabitFrequency = 'DAILY' | 'WEEKLY';

/** A way to group habits, e.g. "Ernährung" or "Sport"; every user has their own. */
export type HabitCategory = {
  id: string;
  name: string;
};

/**
 * A tracked habit as the week grid shows it. Mirrors the backend's HabitWeekResponse.WeekHabit;
 * days travel as ISO dates (`2026-10-09`), never as Date objects, so no time zone shifts them.
 */
export type WeekHabit = {
  id: string;
  name: string;
  /** Null for a habit without a category. */
  categoryId: string | null;
  frequency: HabitFrequency;
  /** Days per week for a weekly habit; null for a daily one. */
  weeklyTarget: number | null;
  /** The ticked days of the shown week, oldest first. */
  checkedDays: string[];
  weekDone: number;
  /** The weekly target, or for a daily habit the days of the week up to today. */
  weekTarget: number;
  /** Days in a row for a daily habit, weeks in a row for a weekly one, up to today. */
  streak: number;
};

/** One calendar week of the signed-in user's habits. */
export type HabitWeek = {
  /** The Monday of the week. */
  start: string;
  /** The day the server counts as today; later days cannot be ticked. */
  today: string;
  /** All categories, alphabetically, empty ones included. */
  categories: HabitCategory[];
  /** The tracked habits in display order: by category, then name, uncategorised last. */
  habits: WeekHabit[];
};

/** What the habit dialog sends. */
export type HabitInput = {
  name: string;
  categoryId: string | null;
  frequency: HabitFrequency;
  /** Required for a weekly habit (1–7); ignored for a daily one. */
  weeklyTarget: number | null;
};

/** An archived habit as the archive dialog lists it. */
export type ArchivedHabit = {
  id: string;
  name: string;
  categoryName: string | null;
  frequency: HabitFrequency;
  weeklyTarget: number | null;
  archivedAt: string;
};

/** A category heading with its habits, as the grid groups them; `category` null for "without". */
export type HabitGroup = {
  category: HabitCategory | null;
  habits: WeekHabit[];
};

/**
 * The habits under their category headings, in the order the backend sent them. Every category
 * gets a heading, empty ones too, so a category created first has a place to fill; the habits
 * without one come last, under a heading only when there are any.
 */
export function groupByCategory(week: HabitWeek): HabitGroup[] {
  const groups: HabitGroup[] = week.categories.map((category) => ({
    category,
    habits: week.habits.filter((habit) => habit.categoryId === category.id),
  }));
  const known = new Set(week.categories.map((category) => category.id));
  const without = week.habits.filter((habit) => habit.categoryId === null || !known.has(habit.categoryId));
  if (without.length > 0) {
    groups.push({ category: null, habits: without });
  }
  return groups;
}
