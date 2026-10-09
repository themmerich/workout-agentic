package de.prime_ux.backend.habits;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HabitCheckRepository extends JpaRepository<HabitCheck, HabitCheck.Key> {

	/**
	 * Every tick of these habits up to a day, in one query: the week grid needs the week, the
	 * streaks need everything before it.
	 */
	@Query("select c from HabitCheck c where c.key.habitId in :habitIds and c.key.day <= :until")
	List<HabitCheck> findAllByHabitIdsUntil(Collection<UUID> habitIds, LocalDate until);

	/** Ticks a day; a day already ticked stays as it is, so a double click is harmless. */
	@Modifying
	@Query(value = "insert into habit_checks (habit_id, day) values (:habitId, :day) on conflict do nothing",
			nativeQuery = true)
	void check(UUID habitId, LocalDate day);

	/** Unticks a day; a day not ticked stays as it is. */
	@Modifying
	@Query(value = "delete from habit_checks where habit_id = :habitId and day = :day", nativeQuery = true)
	void uncheck(UUID habitId, LocalDate day);
}
