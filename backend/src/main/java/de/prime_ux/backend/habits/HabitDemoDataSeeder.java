package de.prime_ux.backend.habits;

import de.prime_ux.backend.tenants.TenantRepository;
import de.prime_ux.backend.users.AppUser;
import de.prime_ux.backend.users.AppUserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the demo tenant's users a few habits with two weeks of ticks, so the start page has
 * something to show right after {@code bootRun}. Dev only, on the same switch as the demo users
 * (see DemoDataSeeder); a user who already has habits is left alone.
 */
@Component
@ConditionalOnBooleanProperty("workout.auth.seed-demo-data")
// After the DemoDataSeeder, which creates the users these habits belong to.
@Order(3)
@Slf4j
class HabitDemoDataSeeder implements ApplicationRunner {

	private static final String DEMO_TENANT = "musterfirma";

	private final TenantRepository tenantRepository;
	private final AppUserRepository appUserRepository;
	private final HabitRepository habitRepository;
	private final HabitCategoryRepository habitCategoryRepository;
	private final HabitCheckRepository habitCheckRepository;

	HabitDemoDataSeeder(TenantRepository tenantRepository, AppUserRepository appUserRepository,
			HabitRepository habitRepository, HabitCategoryRepository habitCategoryRepository,
			HabitCheckRepository habitCheckRepository) {
		this.tenantRepository = tenantRepository;
		this.appUserRepository = appUserRepository;
		this.habitRepository = habitRepository;
		this.habitCategoryRepository = habitCategoryRepository;
		this.habitCheckRepository = habitCheckRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		tenantRepository.findBySlug(DEMO_TENANT).ifPresent(tenant -> {
			for (String username : new String[] { "admin", "user" }) {
				appUserRepository.findByTenantIdAndUsernameIgnoreCase(tenant.getId(), username)
						.filter(user -> !habitRepository.existsByUserId(user.getId()))
						.ifPresent(this::seed);
			}
		});
	}

	private void seed(AppUser user) {
		HabitCategory food = habitCategoryRepository.save(new HabitCategory(user, "Ernährung"));
		HabitCategory sport = habitCategoryRepository.save(new HabitCategory(user, "Sport"));
		Habit vitamins = habitRepository.save(new Habit(user, food, "Multivitamin", HabitFrequency.DAILY, null));
		Habit fibre = habitRepository.save(new Habit(user, food, "Über 20 g Ballaststoffe", HabitFrequency.DAILY, null));
		Habit running = habitRepository.save(new Habit(user, sport, "Laufen", HabitFrequency.WEEKLY, 2));

		// Two weeks back from yesterday, with the gaps a real fortnight has; today is left open.
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		for (int back = 1; back <= 14; back++) {
			LocalDate day = today.minusDays(back);
			if (back % 5 != 0) {
				habitCheckRepository.check(vitamins.getId(), day);
			}
			if (back % 3 != 0) {
				habitCheckRepository.check(fibre.getId(), day);
			}
			if (back % 4 == 1) {
				habitCheckRepository.check(running.getId(), day);
			}
		}
		log.info("Seeded demo habits for user '{}'", user.getUsername());
	}
}
