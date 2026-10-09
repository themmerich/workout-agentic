package de.prime_ux.backend.habits;

import de.prime_ux.backend.habits.HabitWeekResponse.Category;
import de.prime_ux.backend.habits.HabitWeekResponse.WeekHabit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a user's habits and ticks into the week grid. Pure: rows in, response out, today handed
 * in, so a test can pin the rules down without a clock or a database.
 *
 * <p>A week is a calendar week, Monday to Sunday. A daily habit asks for every day of the week
 * that has begun; a weekly one for its target, on any days. The streak looks back from today,
 * whichever week is shown, and does not break on a day or week that is still running: a daily
 * habit not yet ticked today counts up to yesterday, a weekly one whose target this week is not
 * reached yet counts up to last week.
 */
final class HabitWeekReport {

	/** A habit as far as the grid needs it, without the entity behind it. */
	record HabitRow(UUID id, String name, UUID categoryId, HabitFrequency frequency, Integer weeklyTarget) {
	}

	private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

	private HabitWeekReport() {
	}

	/** The Monday of the calendar week a day lies in. */
	static LocalDate mondayOf(LocalDate day) {
		return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/**
	 * @param categories all of the user's categories
	 * @param habits the tracked habits
	 * @param checks the ticked days per habit id, up to today at least as far back as the streaks
	 *        reach; a habit without an entry was never ticked
	 * @param anyDayOfTheWeek the week to show, by any of its days
	 */
	static HabitWeekResponse build(List<Category> categories, List<HabitRow> habits, Map<UUID, Set<LocalDate>> checks,
			LocalDate anyDayOfTheWeek, LocalDate today) {
		LocalDate start = mondayOf(anyDayOfTheWeek);
		List<Category> sortedCategories = categories.stream()
				.sorted(Comparator.comparing(Category::name, BY_NAME))
				.toList();

		Map<UUID, Integer> categoryRank = new HashMap<>();
		for (int i = 0; i < sortedCategories.size(); i++) {
			categoryRank.put(sortedCategories.get(i).id(), i);
		}
		// A category the list does not know — there should be none — sorts with the uncategorised.
		Comparator<HabitRow> order = Comparator
				.comparingInt((HabitRow habit) -> categoryRank.getOrDefault(habit.categoryId(), Integer.MAX_VALUE))
				.thenComparing(HabitRow::name, BY_NAME);

		List<WeekHabit> rows = habits.stream()
				.sorted(order)
				.map(habit -> row(habit, checks.getOrDefault(habit.id(), Set.of()), start, today))
				.toList();
		return new HabitWeekResponse(start, today, sortedCategories, rows);
	}

	private static WeekHabit row(HabitRow habit, Set<LocalDate> checked, LocalDate start, LocalDate today) {
		LocalDate end = start.plusDays(6);
		List<LocalDate> checkedThisWeek = checked.stream()
				.filter(day -> !day.isBefore(start) && !day.isAfter(end))
				.sorted()
				.toList();
		boolean daily = habit.frequency() == HabitFrequency.DAILY;
		int weekTarget = daily ? daysBegun(start, today) : habit.weeklyTarget();
		int streak = daily ? dailyStreak(checked, today) : weeklyStreak(checked, habit.weeklyTarget(), today);
		return new WeekHabit(habit.id(), habit.name(), habit.categoryId(), habit.frequency(), habit.weeklyTarget(),
				checkedThisWeek, checkedThisWeek.size(), weekTarget, streak);
	}

	/** The days of the week starting on {@code start} up to and including today: 0 to 7. */
	static int daysBegun(LocalDate start, LocalDate today) {
		long begun = ChronoUnit.DAYS.between(start, today) + 1;
		return (int) Math.max(0, Math.min(7, begun));
	}

	/** Days in a row up to today, or up to yesterday while today is not ticked yet. */
	static int dailyStreak(Set<LocalDate> checked, LocalDate today) {
		LocalDate day = checked.contains(today) ? today : today.minusDays(1);
		int streak = 0;
		while (checked.contains(day)) {
			streak++;
			day = day.minusDays(1);
		}
		return streak;
	}

	/**
	 * Calendar weeks in a row whose target was reached, up to this week once its target is
	 * reached, up to last week until then.
	 */
	static int weeklyStreak(Set<LocalDate> checked, int target, LocalDate today) {
		Map<LocalDate, Integer> perWeek = new HashMap<>();
		for (LocalDate day : checked) {
			perWeek.merge(mondayOf(day), 1, Integer::sum);
		}
		LocalDate week = mondayOf(today);
		if (perWeek.getOrDefault(week, 0) < target) {
			week = week.minusWeeks(1);
		}
		int streak = 0;
		while (perWeek.getOrDefault(week, 0) >= target) {
			streak++;
			week = week.minusWeeks(1);
		}
		return streak;
	}
}
