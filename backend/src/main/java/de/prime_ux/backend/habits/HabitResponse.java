package de.prime_ux.backend.habits;

import java.time.Instant;
import java.util.UUID;

/**
 * A habit on its own, as creating, changing and the archive list return it; the week grid has
 * its own shape ({@link HabitWeekResponse}).
 *
 * @param archivedAt null while the habit is tracked
 */
public record HabitResponse(UUID id, String name, UUID categoryId, String categoryName, HabitFrequency frequency,
		Integer weeklyTarget, Instant archivedAt) {

	static HabitResponse from(Habit habit) {
		HabitCategory category = habit.getCategory();
		return new HabitResponse(habit.getId(), habit.getName(), category == null ? null : category.getId(),
				category == null ? null : category.getName(), habit.getFrequency(), habit.getWeeklyTarget(),
				habit.getArchivedAt());
	}
}
