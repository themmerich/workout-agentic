package de.prime_ux.backend.habits;

import de.prime_ux.backend.auth.CurrentSession;
import de.prime_ux.backend.habits.HabitWeekReport.HabitRow;
import de.prime_ux.backend.habits.HabitWeekResponse.Category;
import de.prime_ux.backend.users.AppUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in user's own habits and the days they ticked them off. Nobody else's are ever in
 * reach: every lookup goes by the session's user, and another user's habit answers 404 as if it
 * did not exist. The habits are a tenant's user's business, so a super-user without a tenant open
 * is turned away like on every tenant page.
 */
@RestController
@RequestMapping("/api/habits")
class HabitController {

	private final CurrentSession currentSession;
	private final HabitRepository habitRepository;
	private final HabitCategoryRepository habitCategoryRepository;
	private final HabitCheckRepository habitCheckRepository;

	HabitController(CurrentSession currentSession, HabitRepository habitRepository,
			HabitCategoryRepository habitCategoryRepository, HabitCheckRepository habitCheckRepository) {
		this.currentSession = currentSession;
		this.habitRepository = habitRepository;
		this.habitCategoryRepository = habitCategoryRepository;
		this.habitCheckRepository = habitCheckRepository;
	}

	/**
	 * The week grid for the calendar week {@code start} lies in, the current one without it. The
	 * ticks are read up to today in one go, however far back: the streaks need them, and a tick a
	 * day per habit stays a light read for years.
	 */
	@GetMapping("/week")
	@Transactional(readOnly = true)
	HabitWeekResponse getWeek(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
		UUID userId = user().getId();
		LocalDate today = today();
		List<Category> categories = habitCategoryRepository.findAllByUserIdOrderByNameAsc(userId).stream()
				.map(category -> new Category(category.getId(), category.getName()))
				.toList();
		List<HabitRow> habits = habitRepository.findAllByUserIdAndArchivedAtIsNull(userId).stream()
				.map(habit -> new HabitRow(habit.getId(), habit.getName(),
						habit.getCategory() == null ? null : habit.getCategory().getId(), habit.getFrequency(),
						habit.getWeeklyTarget()))
				.toList();
		Map<UUID, Set<LocalDate>> checks = new HashMap<>();
		if (!habits.isEmpty()) {
			habitCheckRepository.findAllByHabitIdsUntil(habits.stream().map(HabitRow::id).toList(), today)
					.forEach(check -> checks.computeIfAbsent(check.getKey().habitId(), id -> new HashSet<>())
							.add(check.getKey().day()));
		}
		return HabitWeekReport.build(categories, habits, checks, start == null ? today : start, today);
	}

	/** Ticks a day; ticking it twice changes nothing. Today and any day before it, never later. */
	@PutMapping("/{id}/checks/{day}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void check(@PathVariable UUID id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
		Habit habit = ownHabit(id);
		if (day.isAfter(today())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a day still to come cannot be ticked");
		}
		habitCheckRepository.check(habit.getId(), day);
	}

	@DeleteMapping("/{id}/checks/{day}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void uncheck(@PathVariable UUID id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
		habitCheckRepository.uncheck(ownHabit(id).getId(), day);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	HabitResponse createHabit(@Valid @RequestBody HabitRequest request) {
		AppUser user = user();
		Habit habit = new Habit(user, ownCategory(request.categoryId(), user), request.name().trim(),
				request.frequency(), request.weeklyTarget());
		return HabitResponse.from(habitRepository.save(habit));
	}

	@PutMapping("/{id}")
	@Transactional
	HabitResponse updateHabit(@PathVariable UUID id, @Valid @RequestBody HabitRequest request) {
		Habit habit = ownHabit(id);
		habit.update(ownCategory(request.categoryId(), habit.getUser()), request.name().trim(), request.frequency(),
				request.weeklyTarget());
		return HabitResponse.from(habitRepository.save(habit));
	}

	/** Final: the ticks go along. Archiving is the way to stop tracking and keep the history. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteHabit(@PathVariable UUID id) {
		habitRepository.delete(ownHabit(id));
	}

	@PutMapping("/{id}/archived")
	@Transactional
	HabitResponse setArchived(@PathVariable UUID id, @Valid @RequestBody ArchiveHabitRequest request) {
		Habit habit = ownHabit(id);
		if (request.archived()) {
			habit.archive(Instant.now());
		} else {
			habit.restore();
		}
		return HabitResponse.from(habitRepository.save(habit));
	}

	@GetMapping("/archived")
	@Transactional(readOnly = true)
	List<HabitResponse> listArchived() {
		return habitRepository.findAllByUserIdAndArchivedAtIsNotNullOrderByNameAsc(user().getId()).stream()
				.map(HabitResponse::from)
				.toList();
	}

	/** The session's user, once the session is about a tenant (403 otherwise). */
	private AppUser user() {
		currentSession.tenant();
		return currentSession.user();
	}

	/**
	 * The server's zone decides where a day ends, as on the AI cost page; the users are German
	 * businesses' people and the server stands where they do.
	 */
	private static LocalDate today() {
		return LocalDate.now(ZoneId.systemDefault());
	}

	private Habit ownHabit(UUID id) {
		return habitRepository.findByIdAndUserId(id, user().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** Null for none; another user's category is not found rather than forbidden. */
	private HabitCategory ownCategory(UUID categoryId, AppUser user) {
		if (categoryId == null) {
			return null;
		}
		return habitCategoryRepository.findByIdAndUserId(categoryId, user.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
	}
}
