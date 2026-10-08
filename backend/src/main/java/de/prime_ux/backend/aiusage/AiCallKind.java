package de.prime_ux.backend.aiusage;

import java.util.Locale;

/** What a call to the model was for. New features that call the model add their own kind here. */
public enum AiCallKind {
	/** Trying a tenant's own key out on the settings page. */
	KEY_TEST;

	/** The kind as the cost page names it: {@code KEY_TEST} becomes {@code keyTest}. */
	public String key() {
		String[] parts = name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder key = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
		}
		return key.toString();
	}
}
