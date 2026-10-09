import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

import { groupByCategory, HabitCategory, HabitWeek, WeekHabit } from '../model/habit';
import { daysOf, isAfter, toDisplayDate } from '../model/week';

/** A day of the shown week, ready for the header and the cells. */
type GridDay = {
  iso: string;
  date: Date;
  isToday: boolean;
  isFuture: boolean;
};

/** A day cell of one habit, as the page asks for it to be flipped. */
export type HabitDayToggle = {
  habit: WeekHabit;
  day: string;
  /** What the cell is to become: ticked or not. */
  checked: boolean;
};

/**
 * The week grid: habits under their category headings, one row each, the seven days of the week
 * as cells to tick, then how far the week got and the streak. Presentational: it shows what it is
 * handed and says what was clicked; the page decides what that means.
 */
@Component({
  selector: 'app-habit-week-table',
  imports: [DatePipe, TranslocoDirective, ButtonModule, TooltipModule],
  templateUrl: './habit-week-table.html',
})
export class HabitWeekTable {
  readonly week = input.required<HabitWeek>();
  /**
   * Cells flipped on the page whose request is still on its way, keyed `habitId|day`, with the
   * state they are to have. They show that state until the reloaded week takes over.
   */
  readonly pending = input<ReadonlyMap<string, boolean>>(new Map());

  readonly dayToggle = output<HabitDayToggle>();
  /** "Add a habit" under a category heading; null for the habits without one. */
  readonly addHabit = output<HabitCategory | null>();
  readonly editHabit = output<WeekHabit>();
  readonly archiveHabit = output<WeekHabit>();
  readonly deleteHabit = output<WeekHabit>();
  readonly renameCategory = output<HabitCategory>();
  readonly deleteCategory = output<HabitCategory>();

  protected readonly days = computed<GridDay[]>(() => {
    const { start, today } = this.week();
    return daysOf(start).map((iso) => ({
      iso,
      date: toDisplayDate(iso),
      isToday: iso === today,
      isFuture: isAfter(iso, today),
    }));
  });

  protected readonly groups = computed(() => groupByCategory(this.week()));

  protected isChecked(habit: WeekHabit, day: string): boolean {
    return this.pending().get(`${habit.id}|${day}`) ?? habit.checkedDays.includes(day);
  }

  /** The week is done once its target is met; a week that has not begun asks nothing yet. */
  protected isWeekReached(habit: WeekHabit): boolean {
    return habit.weekTarget > 0 && habit.weekDone >= habit.weekTarget;
  }

  protected onCell(habit: WeekHabit, day: GridDay): void {
    if (!day.isFuture) {
      this.dayToggle.emit({ habit, day: day.iso, checked: !this.isChecked(habit, day.iso) });
    }
  }
}
