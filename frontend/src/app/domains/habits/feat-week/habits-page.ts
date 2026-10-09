import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ChildFieldContext, form, FormField, required, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';

import { HabitsService } from '../data/habits-service';
import { ArchivedHabit, HabitCategory, HabitFrequency, HabitInput, HabitWeek, WeekHabit } from '../model/habit';
import { addDays, daysOf, isIsoDate, isoWeek, mondayOf, nextWeek, previousWeek, toDisplayDate } from '../model/week';
import { HabitDayToggle, HabitWeekTable } from '../ui/habit-week-table';

type HabitFormModel = {
  name: string;
  /** Null for a habit without a category. */
  categoryId: string | null;
  frequency: HabitFrequency;
  /** Only read for a weekly habit. */
  weeklyTarget: number;
};

type CategoryFormModel = {
  name: string;
};

function emptyHabit(categoryId: string | null = null): HabitFormModel {
  return { name: '', categoryId, frequency: 'DAILY', weeklyTarget: 2 };
}

/** The pending flips the week does not show yet; days of other weeks are left as they are. */
function withoutSettled(pending: ReadonlyMap<string, boolean>, week: HabitWeek): ReadonlyMap<string, boolean> {
  const days = new Set(daysOf(week.start));
  const remaining = new Map(pending);
  for (const [key, checked] of pending) {
    const [habitId, day] = key.split('|');
    const habit = week.habits.find((candidate) => candidate.id === habitId);
    if (habit && days.has(day) && habit.checkedDays.includes(day) === checked) {
      remaining.delete(key);
    }
  }
  return remaining.size === pending.size ? pending : remaining;
}

/**
 * The start page: the signed-in user's habits for one calendar week, to tick off and to manage.
 * The week shown lives in the address (`?week=2026-10-05`), so a reload or a bookmark keeps it.
 */
@Component({
  selector: 'app-habits-page',
  imports: [
    DatePipe,
    FormField,
    TranslocoDirective,
    ButtonModule,
    DialogModule,
    FloatLabelModule,
    HabitWeekTable,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    SelectButtonModule,
  ],
  templateUrl: './habits-page.html',
})
export class HabitsPage {
  protected readonly habitsService = inject(HabitsService);
  private readonly router = inject(Router);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  /** The `week` query parameter: any day of the week to show; absent or malformed for this one. */
  readonly week = input<string>();

  // The service reads the week it is told; the address is what tells it.
  private readonly syncWeek = effect(() => {
    const week = this.week();
    this.habitsService.weekStart.set(isIsoDate(week) ? mondayOf(week) : null);
  });

  /** The loaded week; undefined while it loads or when it failed. */
  protected readonly loadedWeek = computed(() => (this.habitsService.week.hasValue() ? this.habitsService.week.value() : undefined));

  protected readonly heading = computed(() => {
    const week = this.loadedWeek();
    if (!week) {
      return null;
    }
    return { number: isoWeek(week.start), from: toDisplayDate(week.start), to: toDisplayDate(addDays(week.start, 6)) };
  });

  protected readonly isCurrentWeek = computed(() => {
    const week = this.loadedWeek();
    return !week || week.start === mondayOf(week.today);
  });

  protected readonly isEmpty = computed(() => {
    const week = this.loadedWeek();
    return week !== undefined && week.habits.length === 0 && week.categories.length === 0;
  });

  /**
   * Cells flipped here that the loaded week does not show yet, keyed `habitId|day`: the request is
   * still out, or its reload is. A failed request takes its entry back out.
   */
  protected readonly pending = signal<ReadonlyMap<string, boolean>>(new Map());

  // Every week the server sends settles the flips it already shows; the rest stay pending.
  private readonly settlePending = effect(() => {
    const week = this.loadedWeek();
    if (week) {
      untracked(() => this.pending.update((pending) => withoutSettled(pending, week)));
    }
  });

  // Re-evaluates the options once the active translation file (re)loads.
  private readonly translation = toSignal(this.transloco.selectTranslation());

  protected readonly frequencyOptions = computed<{ label: string; value: HabitFrequency }[]>(() => {
    this.translation();
    return [
      { label: this.transloco.translate('habits.daily'), value: 'DAILY' },
      { label: this.transloco.translate('habits.weekly'), value: 'WEEKLY' },
    ];
  });

  protected readonly categoryOptions = computed<{ label: string; value: string | null }[]>(() => {
    this.translation();
    const categories = this.loadedWeek()?.categories ?? [];
    return [
      { label: this.transloco.translate('habits.withoutCategory'), value: null },
      ...categories.map((category) => ({ label: category.name, value: category.id })),
    ];
  });

  // --- habit dialog ---

  /** The habit being edited, or null while the dialog creates one. */
  protected readonly editingHabitId = signal<string | null>(null);
  protected readonly isHabitDialogVisible = signal(false);
  protected readonly isSavingHabit = signal(false);
  // A save attempt judges every field at once — submit() alone does not flip the fields' touched state.
  protected readonly hasHabitSubmitAttempted = signal(false);
  private readonly whenHabitEdited = ({ state }: ChildFieldContext<string>) => state.dirty() || this.hasHabitSubmitAttempted();

  protected readonly habitModel = signal<HabitFormModel>(emptyHabit());
  protected readonly habitForm = form(this.habitModel, (schemaPath) => {
    required(schemaPath.name, { when: this.whenHabitEdited });
  });

  // --- category dialog ---

  /** The category being renamed, or null while the dialog creates one. */
  protected readonly editingCategoryId = signal<string | null>(null);
  protected readonly isCategoryDialogVisible = signal(false);
  protected readonly isSavingCategory = signal(false);
  protected readonly hasCategorySubmitAttempted = signal(false);
  /** The backend said the name is taken; cleared by the next attempt. */
  protected readonly isCategoryNameTaken = signal(false);
  private readonly whenCategoryEdited = ({ state }: ChildFieldContext<string>) => state.dirty() || this.hasCategorySubmitAttempted();

  protected readonly categoryModel = signal<CategoryFormModel>({ name: '' });
  protected readonly categoryForm = form(this.categoryModel, (schemaPath) => {
    required(schemaPath.name, { when: this.whenCategoryEdited });
  });

  // --- archive dialog ---

  protected readonly isArchiveDialogVisible = signal(false);

  // --- week navigation ---

  protected onPreviousWeek(): void {
    const week = this.loadedWeek();
    if (week) {
      this.showWeek(previousWeek(week.start));
    }
  }

  protected onNextWeek(): void {
    const week = this.loadedWeek();
    if (week) {
      this.showWeek(nextWeek(week.start));
    }
  }

  protected onThisWeek(): void {
    this.showWeek(null);
  }

  private showWeek(monday: string | null): void {
    void this.router.navigate([], { queryParams: { week: monday }, queryParamsHandling: 'merge' });
  }

  // --- ticking ---

  /** Flips the cell at once and asks the server; a refusal flips it back and says so. */
  protected async onToggle({ habit, day, checked }: HabitDayToggle): Promise<void> {
    const key = `${habit.id}|${day}`;
    this.pending.update((pending) => new Map(pending).set(key, checked));
    try {
      await (checked ? this.habitsService.check(habit.id, day) : this.habitsService.uncheck(habit.id, day));
    } catch {
      this.pending.update((pending) => {
        const reverted = new Map(pending);
        reverted.delete(key);
        return reverted;
      });
      this.toast('error', 'habits.toggleError');
    }
  }

  // --- habits ---

  protected onAddHabit(category: HabitCategory | null = null): void {
    this.openHabitDialog(null, emptyHabit(category?.id ?? null));
  }

  protected onEditHabit(habit: WeekHabit): void {
    this.openHabitDialog(habit.id, {
      name: habit.name,
      categoryId: habit.categoryId,
      frequency: habit.frequency,
      weeklyTarget: habit.weeklyTarget ?? 2,
    });
  }

  private openHabitDialog(id: string | null, model: HabitFormModel): void {
    this.editingHabitId.set(id);
    this.habitModel.set(model);
    this.habitForm().reset();
    this.hasHabitSubmitAttempted.set(false);
    this.isHabitDialogVisible.set(true);
  }

  protected async onSaveHabit(event: Event): Promise<void> {
    event.preventDefault();
    this.hasHabitSubmitAttempted.set(true);
    await submit(this.habitForm, async () => {
      this.isSavingHabit.set(true);
      const id = this.editingHabitId();
      try {
        const model = this.habitModel();
        const habitInput: HabitInput = {
          name: model.name.trim(),
          categoryId: model.categoryId,
          frequency: model.frequency,
          weeklyTarget: model.frequency === 'WEEKLY' ? model.weeklyTarget : null,
        };
        if (id === null) {
          await this.habitsService.createHabit(habitInput);
        } else {
          await this.habitsService.updateHabit(id, habitInput);
        }
        this.isHabitDialogVisible.set(false);
        this.toast('success', id === null ? 'habits.habitCreated' : 'habits.habitSaved');
      } catch {
        this.toast('error', 'habits.saveError');
      } finally {
        this.isSavingHabit.set(false);
      }
    });
  }

  /** Reversible, so no question asked: the archive brings it back. */
  protected async onArchiveHabit(habit: WeekHabit): Promise<void> {
    try {
      await this.habitsService.setArchived(habit.id, true);
      this.toast('success', 'habits.habitArchived');
    } catch {
      this.toast('error', 'habits.saveError');
    }
  }

  protected onDeleteHabit(habit: { id: string; name: string }): void {
    this.confirm('habits.deleteHabitConfirm', habit.name, async () => {
      try {
        await this.habitsService.deleteHabit(habit.id);
        this.toast('success', 'habits.habitDeleted');
      } catch {
        this.toast('error', 'habits.saveError');
      }
    });
  }

  // --- categories ---

  protected onAddCategory(): void {
    this.openCategoryDialog(null, '');
  }

  protected onRenameCategory(category: HabitCategory): void {
    this.openCategoryDialog(category.id, category.name);
  }

  private openCategoryDialog(id: string | null, name: string): void {
    this.editingCategoryId.set(id);
    this.categoryModel.set({ name });
    this.categoryForm().reset();
    this.hasCategorySubmitAttempted.set(false);
    this.isCategoryNameTaken.set(false);
    this.isCategoryDialogVisible.set(true);
  }

  protected async onSaveCategory(event: Event): Promise<void> {
    event.preventDefault();
    this.hasCategorySubmitAttempted.set(true);
    this.isCategoryNameTaken.set(false);
    await submit(this.categoryForm, async () => {
      this.isSavingCategory.set(true);
      const id = this.editingCategoryId();
      try {
        const name = this.categoryModel().name.trim();
        if (id === null) {
          await this.habitsService.createCategory(name);
        } else {
          await this.habitsService.renameCategory(id, name);
        }
        this.isCategoryDialogVisible.set(false);
        this.toast('success', id === null ? 'habits.categoryCreated' : 'habits.categorySaved');
      } catch (error) {
        // A taken name is the user's to fix, right in the dialog.
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.isCategoryNameTaken.set(true);
        } else {
          this.toast('error', 'habits.saveError');
        }
      } finally {
        this.isSavingCategory.set(false);
      }
    });
  }

  protected onDeleteCategory(category: HabitCategory): void {
    this.confirm('habits.deleteCategoryConfirm', category.name, async () => {
      try {
        await this.habitsService.deleteCategory(category.id);
        this.toast('success', 'habits.categoryDeleted');
      } catch {
        this.toast('error', 'habits.saveError');
      }
    });
  }

  // --- archive ---

  protected onOpenArchive(): void {
    this.habitsService.loadArchive();
    this.isArchiveDialogVisible.set(true);
  }

  protected async onRestoreHabit(habit: ArchivedHabit): Promise<void> {
    try {
      await this.habitsService.setArchived(habit.id, false);
      this.toast('success', 'habits.habitRestored');
    } catch {
      this.toast('error', 'habits.saveError');
    }
  }

  // --- helpers ---

  private confirm(messageKey: string, name: string, accept: () => Promise<void>): void {
    this.confirmationService.confirm({
      header: this.transloco.translate('habits.confirmHeader'),
      message: this.transloco.translate(messageKey, { name }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.transloco.translate('habits.delete'),
      rejectLabel: this.transloco.translate('habits.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => void accept(),
    });
  }

  private toast(severity: 'success' | 'error', translationKey: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(translationKey) });
  }
}
