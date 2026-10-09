package de.prime_ux.backend.habits;

import jakarta.validation.constraints.NotNull;

/** Required rather than defaulted: a body without the flag says nothing and is rejected. */
record ArchiveHabitRequest(@NotNull Boolean archived) {
}
