package de.prime_ux.backend.habits;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A day a habit was done. The row is the tick; a day without one was not done. */
@Entity
@Table(name = "habit_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HabitCheck {

	@EmbeddedId
	private Key key;

	public HabitCheck(UUID habitId, LocalDate day) {
		this.key = new Key(habitId, day);
	}

	@Embeddable
	public record Key(@Column(name = "habit_id") UUID habitId, @Column(name = "day") LocalDate day)
			implements Serializable {
	}
}
