package de.prime_ux.backend.habits;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, UUID> {

	// The category comes along eagerly: the responses carry its id and name, which a lazy proxy
	// could not resolve anymore outside the transaction.
	@EntityGraph(attributePaths = "category")
	List<Habit> findAllByUserIdAndArchivedAtIsNull(UUID userId);

	@EntityGraph(attributePaths = "category")
	List<Habit> findAllByUserIdAndArchivedAtIsNotNullOrderByNameAsc(UUID userId);

	@EntityGraph(attributePaths = "category")
	Optional<Habit> findByIdAndUserId(UUID id, UUID userId);

	boolean existsByUserId(UUID userId);
}
