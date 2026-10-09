package de.prime_ux.backend.habits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.prime_ux.backend.TestcontainersConfiguration;
import de.prime_ux.backend.auth.AsUser;
import de.prime_ux.backend.tenants.Tenant;
import de.prime_ux.backend.tenants.TenantLogoRepository;
import de.prime_ux.backend.tenants.TenantRepository;
import de.prime_ux.backend.users.AppUser;
import de.prime_ux.backend.users.AppUserRepository;
import de.prime_ux.backend.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HabitCategoryControllerTest {

	private static final String NAME_JSON = """
			{"name": "%s"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HabitRepository habitRepository;

	@Autowired
	private HabitCategoryRepository habitCategoryRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private TenantLogoRepository tenantLogoRepository;

	private HabitCategory sport;
	private HabitCategory foreignCategory;
	private Habit running;

	@BeforeEach
	void cleanDatabaseAndCreateCategories() {
		// The habits cascade from their users, the users from their tenants.
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		Tenant tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		AppUser anna = appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}x", UserRole.ADMIN));
		AppUser ben = appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}x", UserRole.USER));
		sport = habitCategoryRepository.save(new HabitCategory(anna, "Sport"));
		habitCategoryRepository.save(new HabitCategory(anna, "Ernährung"));
		running = habitRepository.save(new Habit(anna, sport, "Laufen", HabitFrequency.WEEKLY, 2));
		foreignCategory = habitCategoryRepository.save(new HabitCategory(ben, "Sport"));
	}

	@Test
	@AsUser("anna")
	void listsTheOwnCategoriesAlphabetically() throws Exception {
		mockMvc.perform(get("/api/habit-categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Ernährung"))
				.andExpect(jsonPath("$[1].name").value("Sport"));
	}

	@Test
	@AsUser("anna")
	void createsACategoryWhoseNameIsFree() throws Exception {
		mockMvc.perform(post("/api/habit-categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted(" Schlaf ")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Schlaf"))
				.andExpect(jsonPath("$.id").isNotEmpty());
	}

	@Test
	@AsUser("anna")
	void refusesANameTakenRegardlessOfCase() throws Exception {
		mockMvc.perform(post("/api/habit-categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted("sport")))
				.andExpect(status().isConflict());
		mockMvc.perform(put("/api/habit-categories/" + sport.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted("ERNÄHRUNG")))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/habit-categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted(" ")))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void renamesACategoryAndMayChangeOnlyTheCase() throws Exception {
		mockMvc.perform(put("/api/habit-categories/" + sport.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted("SPORT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("SPORT"));
	}

	@Test
	@AsUser("anna")
	void deletingACategoryKeepsItsHabitsWithoutOne() throws Exception {
		mockMvc.perform(delete("/api/habit-categories/" + sport.getId()).with(csrf()))
				.andExpect(status().isNoContent());

		assertThat(habitCategoryRepository.findById(sport.getId())).isEmpty();
		assertThat(habitRepository.findById(running.getId())).isPresent()
				.get().extracting(Habit::getCategory).isNull();
		mockMvc.perform(get("/api/habits/week"))
				.andExpect(jsonPath("$.habits[0].name").value("Laufen"))
				.andExpect(jsonPath("$.habits[0].categoryId").doesNotExist());
	}

	@Test
	@AsUser("anna")
	void neverReachesAColleaguesCategory() throws Exception {
		mockMvc.perform(put("/api/habit-categories/" + foreignCategory.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(NAME_JSON.formatted("Meins")))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/habit-categories/" + foreignCategory.getId()).with(csrf()))
				.andExpect(status().isNotFound());

		assertThat(habitCategoryRepository.findById(foreignCategory.getId())).isPresent();
	}
}
