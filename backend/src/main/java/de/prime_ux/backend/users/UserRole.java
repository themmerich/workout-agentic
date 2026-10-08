package de.prime_ux.backend.users;

/**
 * The user groups of workout, stored as text in the database. ADMIN unlocks a tenant's
 * administration (users, company, AI settings); USER works in the app. SUPERUSER stands
 * outside the tenants: they manage the tenants themselves and may open any one of them, and then
 * act in it as its admin would.
 */
public enum UserRole {
	ADMIN, USER, SUPERUSER
}
