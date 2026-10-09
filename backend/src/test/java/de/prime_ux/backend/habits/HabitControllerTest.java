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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The habits over the wire: whose they are, what may be ticked, and that the grid reaches the
 * page in the shape it expects. The arithmetic itself is pinned down in {@link HabitWeekReportTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HabitControllerTest {

	private static final String DAILY_JSON = """
			{"name": "%s", "categoryId": %s, "frequency": "DAILY"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HabitRepository habitRepository;

	@Autowired
	private HabitCategoryRepository habitCategoryRepository;

	@Autowired
	private HabitCheckRepository habitCheckRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private TenantLogoRepository tenantLogoRepository;

	private AppUser anna;
	private HabitCategory ernaehrung;
	private Habit vitamins;
	private Habit foreignHabit;
	private HabitCategory foreignCategory;
	private LocalDate today;

	@BeforeEach
	void cleanDatabaseAndCreateHabits() {
		// The habits cascade from their users, the users from their tenants.
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		Tenant tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		anna = appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}x", UserRole.ADMIN));
		AppUser ben = appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}x", UserRole.USER));
		appUserRepository.save(new AppUser(otherTenant, "fritz", "Fritz", "Fremd", "{noop}x", UserRole.USER));
		appUserRepository.save(AppUser.superuser("sina", "Sina", "Super", "{noop}x"));
		ernaehrung = habitCategoryRepository.save(new HabitCategory(anna, "Ernährung"));
		vitamins = habitRepository.save(new Habit(anna, ernaehrung, "Multivitamin", HabitFrequency.DAILY, null));
		// A colleague's habit: same tenant, still none of Anna's business.
		foreignCategory = habitCategoryRepository.save(new HabitCategory(ben, "Bens Kategorie"));
		foreignHabit = habitRepository.save(new Habit(ben, foreignCategory, "Bens Gewohnheit", HabitFrequency.DAILY,
				null));
		today = LocalDate.now(ZoneId.systemDefault());
	}

	@Test
	@AsUser("anna")
	void showsTheCurrentWeekWithTheOwnHabitsOnly() throws Exception {
		habitCheckRepository.save(new HabitCheck(vitamins.getId(), today));

		mockMvc.perform(get("/api/habits/week"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.start").value(HabitWeekReport.mondayOf(today).toString()))
				.andExpect(jsonPath("$.today").value(today.toString()))
				.andExpect(jsonPath("$.categories.length()").value(1))
				.andExpect(jsonPath("$.categories[0].name").value("Ernährung"))
				.andExpect(jsonPath("$.habits.length()").value(1))
				.andExpect(jsonPath("$.habits[0].name").value("Multivitamin"))
				.andExpect(jsonPath("$.habits[0].categoryId").value(ernaehrung.getId().toString()))
				.andExpect(jsonPath("$.habits[0].checkedDays[0]").value(today.toString()))
				.andExpect(jsonPath("$.habits[0].weekDone").value(1))
				.andExpect(jsonPath("$.habits[0].streak").value(1));
	}

	@Test
	@AsUser("anna")
	void showsTheWeekOfTheDayAskedFor() throws Exception {
		mockMvc.perform(get("/api/habits/week").param("start", "2026-10-08"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.start").value("2026-10-05"));
	}

	@Test
	@AsUser("anna")
	void createsAndChangesAHabit() throws Exception {
		String location = mockMvc.perform(post("/api/habits").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": " Laufen ", "frequency": "WEEKLY", "weeklyTarget": 2}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Laufen"))
				.andExpect(jsonPath("$.categoryId").doesNotExist())
				.andExpect(jsonPath("$.weeklyTarget").value(2))
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(location.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

		// Daily now, and in a category; a daily habit carries no target, whatever was sent.
		mockMvc.perform(put("/api/habits/" + id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Laufen", "categoryId": "%s", "frequency": "DAILY", "weeklyTarget": 3}"""
						.formatted(ernaehrung.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.frequency").value("DAILY"))
				.andExpect(jsonPath("$.weeklyTarget").doesNotExist())
				.andExpect(jsonPath("$.categoryName").value("Ernährung"));
	}

	@Test
	@AsUser("anna")
	void rejectsABlankNameAndAWeeklyHabitWithoutAProperTarget() throws Exception {
		for (String body : new String[] { DAILY_JSON.formatted(" ", "null"),
				"{\"name\": \"Laufen\", \"frequency\": \"WEEKLY\"}",
				"{\"name\": \"Laufen\", \"frequency\": \"WEEKLY\", \"weeklyTarget\": 0}",
				"{\"name\": \"Laufen\", \"frequency\": \"WEEKLY\", \"weeklyTarget\": 8}",
				"{\"name\": \"Laufen\"}" }) {
			mockMvc.perform(post("/api/habits").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		assertThat(habitRepository.count()).isEqualTo(2);
	}

	@Test
	@AsUser("anna")
	void refusesAnotherUsersCategory() throws Exception {
		mockMvc.perform(post("/api/habits").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(DAILY_JSON.formatted("Lesen", "\"" + foreignCategory.getId() + "\"")))
				.andExpect(status().isNotFound());
	}

	@Test
	@AsUser("anna")
	void ticksAndUnticksADayTwiceWithoutComplaint() throws Exception {
		String day = "/api/habits/" + vitamins.getId() + "/checks/" + today;

		mockMvc.perform(put(day).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(put(day).with(csrf())).andExpect(status().isNoContent());
		assertThat(habitCheckRepository.count()).isEqualTo(1);

		mockMvc.perform(delete(day).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(delete(day).with(csrf())).andExpect(status().isNoContent());
		assertThat(habitCheckRepository.count()).isZero();
	}

	@Test
	@AsUser("anna")
	void backFillsAPastDayButNotAFutureOne() throws Exception {
		mockMvc.perform(put("/api/habits/" + vitamins.getId() + "/checks/" + today.minusDays(40)).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/habits/" + vitamins.getId() + "/checks/" + today.plusDays(1)).with(csrf()))
				.andExpect(status().isBadRequest());

		assertThat(habitCheckRepository.count()).isEqualTo(1);
	}

	@Test
	@AsUser("anna")
	void archivesAndRestoresAHabitAndKeepsItsTicks() throws Exception {
		habitCheckRepository.save(new HabitCheck(vitamins.getId(), today));

		mockMvc.perform(put("/api/habits/" + vitamins.getId() + "/archived").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"archived\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.archivedAt").isNotEmpty());
		mockMvc.perform(get("/api/habits/week")).andExpect(jsonPath("$.habits.length()").value(0));
		mockMvc.perform(get("/api/habits/archived"))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Multivitamin"));

		mockMvc.perform(put("/api/habits/" + vitamins.getId() + "/archived").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"archived\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.archivedAt").doesNotExist());
		mockMvc.perform(get("/api/habits/week")).andExpect(jsonPath("$.habits[0].weekDone").value(1));

		mockMvc.perform(put("/api/habits/" + vitamins.getId() + "/archived").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void deletesAHabitWithItsTicks() throws Exception {
		habitCheckRepository.save(new HabitCheck(vitamins.getId(), today));

		mockMvc.perform(delete("/api/habits/" + vitamins.getId()).with(csrf())).andExpect(status().isNoContent());

		assertThat(habitRepository.findById(vitamins.getId())).isEmpty();
		assertThat(habitCheckRepository.count()).isZero();
	}

	@Test
	@AsUser("anna")
	void neverReachesAColleaguesHabit() throws Exception {
		String habit = "/api/habits/" + foreignHabit.getId();

		mockMvc.perform(put(habit + "/checks/" + today).with(csrf())).andExpect(status().isNotFound());
		mockMvc.perform(put(habit).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(DAILY_JSON.formatted("Übernommen", "null"))).andExpect(status().isNotFound());
		mockMvc.perform(put(habit + "/archived").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"archived\": true}")).andExpect(status().isNotFound());
		mockMvc.perform(delete(habit).with(csrf())).andExpect(status().isNotFound());

		assertThat(habitRepository.findById(foreignHabit.getId())).isPresent();
		assertThat(habitCheckRepository.count()).isZero();
	}

	@Test
	@AsUser("fritz")
	void showsSomebodyElsesTenantNothingOfIt() throws Exception {
		mockMvc.perform(get("/api/habits/week"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.habits.length()").value(0))
				.andExpect(jsonPath("$.categories.length()").value(0));
		mockMvc.perform(delete("/api/habits/" + vitamins.getId()).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	@AsUser("sina")
	void turnsAwayASuperuserWithoutATenant() throws Exception {
		mockMvc.perform(get("/api/habits/week")).andExpect(status().isForbidden());
	}
}
