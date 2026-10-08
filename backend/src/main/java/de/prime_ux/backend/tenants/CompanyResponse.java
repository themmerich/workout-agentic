package de.prime_ux.backend.tenants;

/** The company's identity and branding; address and contact data belong to its branches. */
public record CompanyResponse(String name, String website, LogoDisplay logoDisplay, String primaryColor,
		boolean hasLogo) {

	/** What stands in for a company while no tenant is open: the app's own name, unbranded. */
	public static CompanyResponse workoutDefault() {
		return new CompanyResponse("workout", null, LogoDisplay.WITH_NAME, null, false);
	}

	public static CompanyResponse from(Tenant tenant, boolean hasLogo) {
		return new CompanyResponse(tenant.getName(), tenant.getWebsite(), tenant.getLogoDisplay(),
				tenant.getPrimaryColor(), hasLogo);
	}
}
