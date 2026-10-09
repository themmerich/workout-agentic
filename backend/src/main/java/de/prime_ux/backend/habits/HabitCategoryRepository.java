package de.prime_ux.backend.habits;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCategoryRepository extends JpaRepository<HabitCategory, UUID> {

	List<HabitCategory> findAllByUserIdOrderByNameAsc(UUID userId);

	Optional<HabitCategory> findByIdAndUserId(UUID id, UUID userId);

	boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}
