package de.prime_ux.backend.habits;

import de.prime_ux.backend.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Something one user wants to do regularly: every day, or a number of days per calendar week.
 * Whether it was done on a day is a {@link HabitCheck}; there is nothing to record for a day it
 * was not.
 */
@Entity
@Table(name = "habits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Habit {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	/** Null for a habit without a category, or one whose category was deleted. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private HabitCategory category;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HabitFrequency frequency;

	/** Days per week for a weekly habit, 1 to 7; null for a daily one. */
	@Column(name = "weekly_target")
	private Integer weeklyTarget;

	/** Out of the week grid since then, history kept; null while the habit is tracked. */
	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Habit(AppUser user, HabitCategory category, String name, HabitFrequency frequency, Integer weeklyTarget) {
		this.user = user;
		this.createdAt = Instant.now();
		update(category, name, frequency, weeklyTarget);
	}

	public void update(HabitCategory category, String name, HabitFrequency frequency, Integer weeklyTarget) {
		this.category = category;
		this.name = name;
		this.frequency = frequency;
		this.weeklyTarget = frequency == HabitFrequency.WEEKLY ? weeklyTarget : null;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public void archive(Instant at) {
		if (archivedAt == null) {
			archivedAt = at;
		}
	}

	public void restore() {
		archivedAt = null;
	}
}
