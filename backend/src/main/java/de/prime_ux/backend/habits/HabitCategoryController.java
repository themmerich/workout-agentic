package de.prime_ux.backend.habits;

import de.prime_ux.backend.auth.CurrentSession;
import de.prime_ux.backend.habits.HabitWeekResponse.Category;
import de.prime_ux.backend.users.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in user's own habit categories. A name is unique per user, regardless of case; a
 * taken one answers 409. Deleting a category keeps its habits, which are then without one.
 */
@RestController
@RequestMapping("/api/habit-categories")
class HabitCategoryController {

	private final CurrentSession currentSession;
	private final HabitCategoryRepository habitCategoryRepository;

	HabitCategoryController(CurrentSession currentSession, HabitCategoryRepository habitCategoryRepository) {
		this.currentSession = currentSession;
		this.habitCategoryRepository = habitCategoryRepository;
	}

	@GetMapping
	List<Category> listCategories() {
		return habitCategoryRepository.findAllByUserIdOrderByNameAsc(user().getId()).stream()
				.map(HabitCategoryController::toResponse)
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	Category createCategory(@Valid @RequestBody HabitCategoryRequest request) {
		AppUser user = user();
		String name = request.name().trim();
		if (habitCategoryRepository.existsByUserIdAndNameIgnoreCase(user.getId(), name)) {
			throw nameTaken();
		}
		return toResponse(habitCategoryRepository.save(new HabitCategory(user, name)));
	}

	@PutMapping("/{id}")
	@Transactional
	Category renameCategory(@PathVariable UUID id, @Valid @RequestBody HabitCategoryRequest request) {
		HabitCategory category = ownCategory(id);
		String name = request.name().trim();
		boolean nameTaken = !name.equalsIgnoreCase(category.getName())
				&& habitCategoryRepository.existsByUserIdAndNameIgnoreCase(category.getUser().getId(), name);
		if (nameTaken) {
			throw nameTaken();
		}
		category.rename(name);
		return toResponse(habitCategoryRepository.save(category));
	}

	/** The habits stay, without a category (FK SET NULL). */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteCategory(@PathVariable UUID id) {
		habitCategoryRepository.delete(ownCategory(id));
	}

	/** The session's user, once the session is about a tenant (403 otherwise). */
	private AppUser user() {
		currentSession.tenant();
		return currentSession.user();
	}

	private HabitCategory ownCategory(UUID id) {
		return habitCategoryRepository.findByIdAndUserId(id, user().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseStatusException nameTaken() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "a category with this name already exists");
	}

	private static Category toResponse(HabitCategory category) {
		return new Category(category.getId(), category.getName());
	}
}
