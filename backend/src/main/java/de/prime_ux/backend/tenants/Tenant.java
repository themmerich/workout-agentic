package de.prime_ux.backend.tenants;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * A tenant is one customer of workout. Every tenant user belongs to exactly one tenant, and all
 * business data is scoped to a tenant so a single deployment can serve many customers. Only the
 * super-users stand outside.
 */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false)
	private String name;

	/**
	 * The Kennung: what a person types on the login page to say which tenant they belong to.
	 * Lower case, letters, digits and single dashes; unique.
	 */
	@Column(nullable = false)
	private String slug;

	// Address and contact data live on the headquarters branch; the tenant
	// keeps only its identity and branding.
	private String website;

	@Enumerated(EnumType.STRING)
	@Column(name = "logo_display", nullable = false)
	private LogoDisplay logoDisplay;

	/** Brand color as hex (#RRGGBB); the app's default primary color for this tenant's users. */
	@Column(name = "primary_color")
	private String primaryColor;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Tenant(String name, String slug) {
		this.name = name;
		this.slug = slug;
		this.logoDisplay = LogoDisplay.WITH_NAME;
		this.createdAt = Instant.now();
	}

	/** What the Mandanten page manages: the name and the Kennung. */
	public void updateIdentity(String name, String slug) {
		this.name = name;
		this.slug = slug;
	}

	/** The name is the tenant's name everywhere — renaming here renames the tenant. */
	public void updateCompany(String name, String website, LogoDisplay logoDisplay, String primaryColor) {
		this.name = name;
		this.website = website;
		this.logoDisplay = logoDisplay;
		this.primaryColor = primaryColor;
	}
}
