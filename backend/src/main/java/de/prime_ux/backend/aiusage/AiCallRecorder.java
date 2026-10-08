package de.prime_ux.backend.aiusage;

import de.prime_ux.backend.tenants.Tenant;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Writes down a call to the model. What every place that calls the model depends on, so a test can
 * stand something in that only remembers what it was asked to record.
 */
public interface AiCallRecorder {

	/**
	 * @param response what the model answered; its metadata carries the tokens and the model
	 */
	void record(Tenant tenant, AiCallKind kind, ChatResponse response);
}
