package de.prime_ux.backend.habits;

import de.prime_ux.backend.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** A way for one user to group their habits, e.g. "Ernährung" or "Sport". */
@Entity
@Table(name = "habit_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HabitCategory {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public HabitCategory(AppUser user, String name) {
		this.user = user;
		this.name = name;
		this.createdAt = Instant.now();
	}

	public void rename(String name) {
		this.name = name;
	}
}
