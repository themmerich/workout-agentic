package de.prime_ux.backend.aiusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.prime_ux.backend.TestcontainersConfiguration;
import de.prime_ux.backend.aiusage.AiPrices.ModelPrice;
import de.prime_ux.backend.branches.BranchRepository;
import de.prime_ux.backend.tenants.Tenant;
import de.prime_ux.backend.tenants.TenantLogoRepository;
import de.prime_ux.backend.tenants.TenantRepository;
import de.prime_ux.backend.users.AppUserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What is written down about a call. Against the database, because the one thing that matters
 * beyond the arithmetic is that the row survives whatever the caller does afterwards.
 */
@SpringBootTest(properties = {
		// A model of the test's own, so the assertion does not depend on the real price list.
		"workout.ai.prices.test-model.input-per-million=1",
		"workout.ai.prices.test-model.output-per-million=2" })
@Import(TestcontainersConfiguration.class)
class AiCallsTest {

	@Autowired
	private AiCalls aiCalls;

	@Autowired
	private AiCallRepository aiCallRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private TenantLogoRepository tenantLogoRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Tenant tenant;

	@BeforeEach
	void cleanDatabase() {
		aiCallRepository.deleteAll();
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		branchRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
	}

	@Test
	void pricesTheTokensPerMillion() {
		ModelPrice price = new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00"));

		// 1200 in at $2/M is $0.0024, 300 out at $10/M is $0.003.
		assertThat(AiCalls.cost(1200, 300, price)).isEqualByComparingTo("0.005400");
		assertThat(AiCalls.cost(0, 0, price)).isEqualByComparingTo("0");
	}

	@Test
	void recordsTheCallWithItsTokensAndWhatTheyWereWorth() {
		aiCalls.record(tenant, AiCallKind.KEY_TEST, ChatResponses.of("ok", "test-model", 1_000_000, 500_000));

		AiCall call = aiCallRepository.findAll().getFirst();
		assertThat(call.getKind()).isEqualTo(AiCallKind.KEY_TEST);
		assertThat(call.getModel()).isEqualTo("test-model");
		assertThat(call.getInputTokens()).isEqualTo(1_000_000);
		assertThat(call.getOutputTokens()).isEqualTo(500_000);
		// $1 for the million in, $1 for the half million out at $2 per million.
		assertThat(call.getCostUsd()).isEqualByComparingTo("2.000000");
		assertThat(call.getCalledAt()).isNotNull();
	}

	@Test
	void recordsAModelWithoutAPriceWithoutAnAmount() {
		aiCalls.record(tenant, AiCallKind.KEY_TEST, ChatResponses.of("ok", "claude-next-99", 10, 5));

		AiCall call = aiCallRepository.findAll().getFirst();
		assertThat(call.getCostUsd()).isNull();
		assertThat(call.getInputTokens()).isEqualTo(10);
	}

	@Test
	void recordsNothingWhenTheAnswerSaysNothingAboutItsUsage() {
		aiCalls.record(tenant, AiCallKind.KEY_TEST, ChatResponses.withoutUsage("ok"));
		aiCalls.record(tenant, AiCallKind.KEY_TEST, null);

		assertThat(aiCallRepository.count()).isZero();
	}

	@Test
	void keepsTheRowWhenTheCallersTransactionRollsBack() {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			aiCalls.record(tenant, AiCallKind.KEY_TEST, ChatResponses.of("ok", "test-model", 10, 5));
			// The caller judging the answer unusable, and taking its own work back.
			throw new IllegalStateException("no usable answer");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(aiCallRepository.count()).isEqualTo(1);
	}
}
