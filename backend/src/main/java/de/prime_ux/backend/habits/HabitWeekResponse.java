package de.prime_ux.backend.habits;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One calendar week of the signed-in user's habits, as the week grid shows it: every category,
 * every tracked habit in display order, the days ticked in that week, how far the week got and
 * the streak up to today.
 *
 * @param start the Monday of the week
 * @param today the day the server counts as today; days after it cannot be ticked
 * @param categories all of the user's categories, alphabetically, empty ones included
 * @param habits the tracked habits, by category name then habit name, those without a category
 *        last
 */
public record HabitWeekResponse(LocalDate start, LocalDate today, List<Category> categories, List<WeekHabit> habits) {

	public record Category(UUID id, String name) {
	}

	/**
	 * @param categoryId null for a habit without a category
	 * @param weeklyTarget days per week for a weekly habit; null for a daily one
	 * @param checkedDays the days of this week that were ticked, oldest first
	 * @param weekDone how many days of this week were ticked
	 * @param weekTarget what the week asks for: the weekly target, or for a daily habit the days of
	 *        the week up to today
	 * @param streak days in a row for a daily habit, weeks in a row for a weekly one, up to today
	 */
	public record WeekHabit(UUID id, String name, UUID categoryId, HabitFrequency frequency, Integer weeklyTarget,
			List<LocalDate> checkedDays, int weekDone, int weekTarget, int streak) {
	}
}
