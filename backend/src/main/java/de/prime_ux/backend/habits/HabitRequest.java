package de.prime_ux.backend.habits;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create and update share the same shape.
 *
 * @param categoryId null for a habit without a category
 * @param weeklyTarget days per week, 1 to 7, for a weekly habit; ignored for a daily one
 */
record HabitRequest(@NotBlank @Size(max = 200) String name, UUID categoryId, @NotNull HabitFrequency frequency,
		Integer weeklyTarget) {

	/** A weekly habit says how many days; a daily one needs nothing. */
	@AssertTrue
	boolean isWeeklyTargetValid() {
		return frequency != HabitFrequency.WEEKLY || (weeklyTarget != null && weeklyTarget >= 1 && weeklyTarget <= 7);
	}
}
