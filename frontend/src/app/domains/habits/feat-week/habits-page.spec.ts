import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Confirmation, ConfirmationService, MessageService, ToastMessageOptions } from 'primeng/api';

import { HabitsService } from '../data/habits-service';
import { ArchivedHabit, HabitInput, HabitWeek, WeekHabit } from '../model/habit';
import { HabitsPage } from './habits-page';

const translations = {
  habits: {
    title: 'Habits',
    newHabit: 'New habit',
    editHabit: 'Edit habit',
    newCategory: 'New category',
    renameCategory: 'Rename category',
    archive: 'Archive',
    archiveTitle: 'Archived habits',
    archiveEmpty: 'No archived habits.',
    restore: 'Restore',
    restoreNamed: 'Restore {{name}}',
    calendarWeek: 'Week {{number}}',
    previousWeek: 'Previous week',
    nextWeek: 'Next week',
    thisWeek: 'This week',
    withoutCategory: 'Without category',
    daily: 'daily',
    weekly: 'per week',
    emptyHint: 'Add what you want to do regularly.',
    firstHabit: 'Add your first habit',
    name: 'Name',
    nameRequired: 'Please enter a name.',
    category: 'Category',
    frequency: 'Frequency',
    daysPerWeek: 'days per week',
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    confirmHeader: 'Really delete?',
    deleteHabitConfirm: '“{{name}}” will be deleted.',
    deleteCategoryConfirm: 'The category “{{name}}” will be deleted.',
    deleteHabitNamed: 'Delete {{name}}',
    categoryNameTaken: 'This name is already taken.',
    habitCreated: 'Habit added.',
    habitArchived: 'Habit archived.',
    habitRestored: 'Habit restored.',
    habitDeleted: 'Habit deleted.',
    categoryCreated: 'Category added.',
    saveError: 'That did not work.',
    toggleError: 'The tick could not be saved.',
    loadError: 'The habits could not be loaded.',
    loading: 'Loading',
  },
};

const vitamins: WeekHabit = {
  id: 'h1',
  name: 'Multivitamin',
  categoryId: 'c1',
  frequency: 'DAILY',
  weeklyTarget: null,
  checkedDays: ['2026-10-05'],
  weekDone: 1,
  weekTarget: 5,
  streak: 0,
};

const week: HabitWeek = {
  start: '2026-10-05',
  today: '2026-10-09',
  categories: [{ id: 'c1', name: 'Ernährung' }],
  habits: [vitamins],
};

describe('HabitsPage', () => {
  const weekValue = signal<HabitWeek | undefined>(week);
  const weekError = signal<Error | undefined>(undefined);
  const archivedValue = signal<ArchivedHabit[]>([]);
  const weekStart = signal<string | null>(null);
  let calls: string[];
  let createdHabits: HabitInput[];
  let failNext: unknown;
  let confirmations: Confirmation[];
  let toasts: ToastMessageOptions[];

  /** Resolves, or rejects with what the test put up for the next call. */
  function answer(call: string): Promise<void> {
    calls.push(call);
    const failure = failNext;
    failNext = undefined;
    return failure === undefined ? Promise.resolve() : Promise.reject(failure);
  }

  const habitsServiceStub = {
    weekStart,
    week: { value: weekValue, error: weekError, hasValue: () => weekValue() !== undefined },
    archived: { value: archivedValue, error: signal(undefined) },
    loadArchive: () => calls.push('loadArchive'),
    check: (id: string, day: string) => answer(`check ${id} ${day}`),
    uncheck: (id: string, day: string) => answer(`uncheck ${id} ${day}`),
    createHabit: (input: HabitInput) => {
      createdHabits.push(input);
      return answer('createHabit');
    },
    updateHabit: (id: string) => answer(`updateHabit ${id}`),
    deleteHabit: (id: string) => answer(`deleteHabit ${id}`),
    setArchived: (id: string, archived: boolean) => answer(`setArchived ${id} ${archived}`),
    createCategory: (name: string) => answer(`createCategory ${name}`),
    renameCategory: (id: string, name: string) => answer(`renameCategory ${id} ${name}`),
    deleteCategory: (id: string) => answer(`deleteCategory ${id}`),
  } as unknown as HabitsService;

  beforeEach(async () => {
    weekValue.set(week);
    weekError.set(undefined);
    archivedValue.set([]);
    weekStart.set(null);
    calls = [];
    createdHabits = [];
    failNext = undefined;
    confirmations = [];
    toasts = [];
    await TestBed.configureTestingModule({
      imports: [
        HabitsPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: HabitsService, useValue: habitsServiceStub },
        { provide: MessageService, useValue: { add: (toast: ToastMessageOptions) => toasts.push(toast) } },
        { provide: ConfirmationService, useValue: { confirm: (confirmation: Confirmation) => confirmations.push(confirmation) } },
      ],
    }).compileComponents();
  });

  function createFixture(weekParam?: string) {
    const fixture = TestBed.createComponent(HabitsPage);
    if (weekParam !== undefined) {
      fixture.componentRef.setInput('week', weekParam);
    }
    fixture.detectChanges();
    return fixture;
  }

  function buttonLabelled(element: HTMLElement | Document, text: string): HTMLButtonElement {
    return Array.from(element.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === text || button.getAttribute('aria-label') === text,
    ) as HTMLButtonElement;
  }

  it('shows the week with its number and the grid', () => {
    const element = createFixture().nativeElement as HTMLElement;

    expect(element.querySelector('h2')?.textContent).toContain('Week 41');
    expect(element.querySelector('app-habit-week-table')?.textContent).toContain('Multivitamin');
    // The current week is shown; there is nowhere nearer to go.
    expect(buttonLabelled(element, 'This week').disabled).toBe(true);
  });

  it('follows the week in the address and leaves a malformed one for the current week', () => {
    createFixture('2026-09-30');
    expect(weekStart()).toBe('2026-09-28');

    const fixture = createFixture('yesterday');
    fixture.detectChanges();
    expect(weekStart()).toBeNull();
  });

  it('steps a week back and forth through the address', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const element = createFixture().nativeElement as HTMLElement;

    buttonLabelled(element, 'Previous week').click();
    buttonLabelled(element, 'Next week').click();

    expect(navigate.mock.calls.map(([, extras]) => extras?.queryParams)).toEqual([{ week: '2026-09-28' }, { week: '2026-10-12' }]);
  });

  it('ticks a day at once and keeps it once the server agreed', async () => {
    const fixture = createFixture();
    const element = fixture.nativeElement as HTMLElement;

    // Tuesday, not ticked yet.
    const cells = Array.from(element.querySelectorAll<HTMLButtonElement>('button[aria-pressed]'));
    cells[1].click();
    fixture.detectChanges();
    expect(cells[1].getAttribute('aria-pressed')).toBe('true');
    await fixture.whenStable();

    expect(calls).toEqual(['check h1 2026-10-06']);
    expect(cells[1].getAttribute('aria-pressed')).toBe('true');
  });

  it('flips a tick back and says so when the server refuses', async () => {
    const fixture = createFixture();
    const element = fixture.nativeElement as HTMLElement;
    failNext = new HttpErrorResponse({ status: 500 });

    const monday = element.querySelectorAll<HTMLButtonElement>('button[aria-pressed]')[0];
    monday.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(calls).toEqual(['uncheck h1 2026-10-05']);
    expect(monday.getAttribute('aria-pressed')).toBe('true');
    expect(toasts[0].summary).toBe('The tick could not be saved.');
  });

  it('adds a weekly habit through the dialog', async () => {
    const fixture = createFixture();
    const element = fixture.nativeElement as HTMLElement;

    buttonLabelled(element, 'New habit').click();
    await fixture.whenStable();
    const name = document.querySelector('#habitName') as HTMLInputElement;
    name.value = '  Laufen ';
    name.dispatchEvent(new Event('input'));
    // The select-button's options are toggle buttons of PrimeNG's own making.
    (
      Array.from(document.querySelectorAll<HTMLElement>('.p-togglebutton')).find(
        (option) => option.textContent?.trim() === 'per week',
      ) as HTMLElement
    ).click();
    await fixture.whenStable();
    (document.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(createdHabits).toEqual([{ name: 'Laufen', categoryId: null, frequency: 'WEEKLY', weeklyTarget: 2 }]);
    expect(toasts[0].summary).toBe('Habit added.');
  });

  it('refuses a nameless habit before calling the backend', async () => {
    const fixture = createFixture();
    buttonLabelled(fixture.nativeElement as HTMLElement, 'New habit').click();
    await fixture.whenStable();

    (document.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(createdHabits).toEqual([]);
    expect(document.body.textContent).toContain('Please enter a name.');
  });

  it('says in the dialog when a category name is taken', async () => {
    const fixture = createFixture();
    buttonLabelled(fixture.nativeElement as HTMLElement, 'New category').click();
    await fixture.whenStable();
    const name = document.querySelector('#categoryName') as HTMLInputElement;
    name.value = 'Ernährung';
    name.dispatchEvent(new Event('input'));
    failNext = new HttpErrorResponse({ status: 409 });

    (Array.from(document.querySelectorAll('form')).find((form) => form.querySelector('#categoryName')) as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(calls).toEqual(['createCategory Ernährung']);
    expect(document.body.textContent).toContain('This name is already taken.');
    expect(toasts).toEqual([]);
  });

  it('asks before deleting a habit, and archives without asking', async () => {
    const fixture = createFixture();
    const table = fixture.debugElement.query((debug) => debug.name === 'app-habit-week-table').componentInstance;

    table.deleteHabit.emit(vitamins);
    expect(confirmations[0].message).toBe('“Multivitamin” will be deleted.');
    expect(calls).toEqual([]);
    confirmations[0].accept?.();
    await fixture.whenStable();
    expect(calls).toEqual(['deleteHabit h1']);

    table.archiveHabit.emit(vitamins);
    await fixture.whenStable();
    expect(calls).toEqual(['deleteHabit h1', 'setArchived h1 true']);
    expect(toasts.map((toast) => toast.summary)).toEqual(['Habit deleted.', 'Habit archived.']);
  });

  it('lists the archive and restores from it', async () => {
    archivedValue.set([
      { id: 'h9', name: 'Kalt duschen', categoryName: null, frequency: 'DAILY', weeklyTarget: null, archivedAt: '2026-10-01T08:00:00Z' },
    ]);
    const fixture = createFixture();

    buttonLabelled(fixture.nativeElement as HTMLElement, 'Archive').click();
    await fixture.whenStable();
    buttonLabelled(document, 'Restore Kalt duschen').click();
    await fixture.whenStable();

    expect(calls).toEqual(['loadArchive', 'setArchived h9 false']);
    expect(toasts[0].summary).toBe('Habit restored.');
  });

  it('invites the first habit while there is nothing yet', () => {
    weekValue.set({ ...week, categories: [], habits: [] });
    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Add what you want to do regularly.');
    expect(buttonLabelled(element, 'Add your first habit')).toBeTruthy();
    expect(element.querySelector('app-habit-week-table')).toBeNull();
  });

  it('says so when the week cannot be loaded', () => {
    weekValue.set(undefined);
    weekError.set(new Error('down'));
    const element = createFixture().nativeElement as HTMLElement;

    expect(element.textContent).toContain('The habits could not be loaded.');
  });
});
