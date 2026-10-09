package de.prime_ux.backend.habits;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.backend.habits.HabitWeekReport.HabitRow;
import de.prime_ux.backend.habits.HabitWeekResponse.Category;
import de.prime_ux.backend.habits.HabitWeekResponse.WeekHabit;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The rules behind the week grid, with today in hand. */
class HabitWeekReportTest {

	/** A Friday; its week runs from Monday 5 to Sunday 11 October 2026. */
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

	private static final Category ERNAEHRUNG = new Category(UUID.randomUUID(), "Ernährung");
	private static final Category SPORT = new Category(UUID.randomUUID(), "sport");

	private static HabitRow daily(String name, Category category) {
		return new HabitRow(UUID.randomUUID(), name, category == null ? null : category.id(), HabitFrequency.DAILY,
				null);
	}

	private static HabitRow weekly(String name, int target) {
		return new HabitRow(UUID.randomUUID(), name, SPORT.id(), HabitFrequency.WEEKLY, target);
	}

	private static Set<LocalDate> days(String... isoDates) {
		return Arrays.stream(isoDates).map(LocalDate::parse).collect(Collectors.toSet());
	}

	private static WeekHabit only(HabitRow habit, Set<LocalDate> checked, LocalDate week) {
		return HabitWeekReport.build(List.of(ERNAEHRUNG, SPORT), List.of(habit), Map.of(habit.id(), checked), week, TODAY)
				.habits().getFirst();
	}

	@Test
	void countsADailyHabitAgainstTheDaysTheWeekHasBegun() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		WeekHabit row = only(vitamins, days("2026-10-04", "2026-10-05", "2026-10-06", "2026-10-08"), TODAY);

		// Monday to Friday have begun; Sunday the 4th belongs to last week.
		assertThat(row.weekTarget()).isEqualTo(5);
		assertThat(row.weekDone()).isEqualTo(3);
		assertThat(row.checkedDays()).containsExactly(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"),
				LocalDate.parse("2026-10-08"));
	}

	@Test
	void asksAPastWeekForAllSevenDaysAndAFutureWeekForNone() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		assertThat(only(vitamins, days(), MONDAY.minusWeeks(1)).weekTarget()).isEqualTo(7);
		assertThat(only(vitamins, days(), MONDAY.plusWeeks(1)).weekTarget()).isZero();
	}

	@Test
	void countsAWeeklyHabitAgainstItsTarget() {
		HabitRow running = weekly("Laufen", 2);

		WeekHabit row = only(running, days("2026-10-06"), TODAY);

		assertThat(row.weekTarget()).isEqualTo(2);
		assertThat(row.weekDone()).isEqualTo(1);
		assertThat(row.weeklyTarget()).isEqualTo(2);
	}

	@Test
	void showsTheWeekOfAnyDayHandedIn() {
		HabitWeekResponse week = HabitWeekReport.build(List.of(), List.of(), Map.of(), LocalDate.parse("2026-10-08"),
				TODAY);

		assertThat(week.start()).isEqualTo(MONDAY);
		assertThat(week.today()).isEqualTo(TODAY);
	}

	@Test
	void countsTheDailyStreakUpToToday() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		assertThat(only(vitamins, days("2026-10-07", "2026-10-08", "2026-10-09"), TODAY).streak()).isEqualTo(3);
	}

	@Test
	void keepsTheDailyStreakWhileTodayIsStillOpen() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		// Nothing yet today: the run up to yesterday still stands.
		assertThat(only(vitamins, days("2026-10-07", "2026-10-08"), TODAY).streak()).isEqualTo(2);
		// A gap yesterday ends it.
		assertThat(only(vitamins, days("2026-10-06", "2026-10-07"), TODAY).streak()).isZero();
	}

	@Test
	void breaksTheDailyStreakAtAGap() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		WeekHabit row = only(vitamins, days("2026-10-01", "2026-10-02", "2026-10-04", "2026-10-05", "2026-10-09"),
				TODAY);

		// Today alone: the 8th is missing.
		assertThat(row.streak()).isEqualTo(1);
	}

	@Test
	void countsTheWeeklyStreakWithThisWeekOnceItsTargetIsReached() {
		HabitRow running = weekly("Laufen", 2);
		// Two runs in each of the three weeks before, and two this week already.
		Set<LocalDate> runs = days("2026-09-14", "2026-09-16", "2026-09-22", "2026-09-26", "2026-09-29",
				"2026-10-02", "2026-10-06", "2026-10-08");

		assertThat(only(running, runs, TODAY).streak()).isEqualTo(4);
	}

	@Test
	void keepsTheWeeklyStreakWhileThisWeekIsStillShort() {
		HabitRow running = weekly("Laufen", 2);
		Set<LocalDate> runs = days("2026-09-22", "2026-09-26", "2026-09-29", "2026-10-02", "2026-10-06");

		// One run this week: not reached yet, but the two weeks before stand.
		assertThat(only(running, runs, TODAY).streak()).isEqualTo(2);
	}

	@Test
	void breaksTheWeeklyStreakAtAWeekBelowTarget() {
		HabitRow running = weekly("Laufen", 2);
		// The week of 21 September had one run only.
		Set<LocalDate> runs = days("2026-09-14", "2026-09-16", "2026-09-22", "2026-09-29", "2026-10-02");

		assertThat(only(running, runs, TODAY).streak()).isEqualTo(1);
	}

	@Test
	void ordersByCategoryThenNameWithTheUncategorisedLast() {
		HabitRow fibre = daily("Ballaststoffe", ERNAEHRUNG);
		HabitRow vitamins = daily("multivitamin", ERNAEHRUNG);
		HabitRow running = weekly("Laufen", 2);
		HabitRow reading = daily("Abends lesen", null);

		HabitWeekResponse week = HabitWeekReport.build(List.of(SPORT, ERNAEHRUNG),
				List.of(reading, running, vitamins, fibre), Map.of(), TODAY, TODAY);

		assertThat(week.categories()).extracting(Category::name).containsExactly("Ernährung", "sport");
		assertThat(week.habits()).extracting(WeekHabit::name)
				.containsExactly("Ballaststoffe", "multivitamin", "Laufen", "Abends lesen");
	}

	@Test
	void treatsAHabitWithoutTicksAsNeverDone() {
		HabitRow vitamins = daily("Multivitamin", ERNAEHRUNG);

		WeekHabit row = HabitWeekReport.build(List.of(ERNAEHRUNG), List.of(vitamins), Map.of(), TODAY, TODAY)
				.habits().getFirst();

		assertThat(row.weekDone()).isZero();
		assertThat(row.streak()).isZero();
		assertThat(row.checkedDays()).isEmpty();
	}
}
