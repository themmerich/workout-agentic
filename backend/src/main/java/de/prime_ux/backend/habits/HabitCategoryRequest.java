package de.prime_ux.backend.habits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record HabitCategoryRequest(@NotBlank @Size(max = 100) String name) {
}
