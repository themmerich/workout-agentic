import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { HabitCategory, HabitWeek, WeekHabit } from '../model/habit';
import { HabitDayToggle, HabitWeekTable } from './habit-week-table';

const translations = {
  habits: {
    tableCaption: 'Habits of the week',
    habit: 'Habit',
    week: 'Week',
    streak: 'Streak',
    withoutCategory: 'Without category',
    daily: 'daily',
    timesPerWeek: '{{count}}× per week',
    streakDay: '{{count}} day',
    streakDays: '{{count}} days',
    streakWeek: '{{count}} week',
    streakWeeks: '{{count}} weeks',
    editHabit: 'Edit',
    editHabitNamed: 'Edit {{name}}',
    archiveHabit: 'Archive',
    archiveHabitNamed: 'Archive {{name}}',
    deleteHabit: 'Delete',
    deleteHabitNamed: 'Delete {{name}}',
    renameCategory: 'Rename',
    renameCategoryNamed: 'Rename {{name}}',
    deleteCategory: 'Delete',
    deleteCategoryNamed: 'Delete {{name}}',
    emptyCategory: 'No habits yet.',
    addToCategory: 'Add a habit',
  },
  common: {
    actions: 'Actions',
  },
};

const ernaehrung: HabitCategory = { id: 'c1', name: 'Ernährung' };
const sport: HabitCategory = { id: 'c2', name: 'Sport' };

const vitamins: WeekHabit = {
  id: 'h1',
  name: 'Multivitamin',
  categoryId: 'c1',
  frequency: 'DAILY',
  weeklyTarget: null,
  checkedDays: ['2026-10-05', '2026-10-08'],
  weekDone: 2,
  weekTarget: 5,
  streak: 1,
};

const running: WeekHabit = {
  id: 'h2',
  name: 'Laufen',
  categoryId: null,
  frequency: 'WEEKLY',
  weeklyTarget: 2,
  checkedDays: ['2026-10-06', '2026-10-08'],
  weekDone: 2,
  weekTarget: 2,
  streak: 5,
};

/** Friday, 9 October 2026; Saturday and Sunday are still to come. */
const week: HabitWeek = { start: '2026-10-05', today: '2026-10-09', categories: [ernaehrung, sport], habits: [vitamins, running] };

describe('HabitWeekTable', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HabitWeekTable,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  function createFixture(pending = new Map<string, boolean>()) {
    const fixture = TestBed.createComponent(HabitWeekTable);
    fixture.componentRef.setInput('week', week);
    fixture.componentRef.setInput('pending', pending);
    fixture.detectChanges();
    return fixture;
  }

  function cell(element: HTMLElement, label: string): HTMLButtonElement {
    return element.querySelector(`button[aria-label^="${label}"]`) as HTMLButtonElement;
  }

  it('groups the habits under their categories, empty ones included, the uncategorised last', () => {
    const element = createFixture().nativeElement as HTMLElement;

    const headings = Array.from(element.querySelectorAll('th[scope="colgroup"]')).map((heading) => heading.textContent?.trim());
    expect(headings).toEqual(['Ernährung', 'Sport', 'Without category']);
    const rows = Array.from(element.querySelectorAll('th[scope="row"]')).map((row) => row.textContent?.replace(/\s+/g, ' ').trim());
    expect(rows).toEqual(['Multivitamin daily', 'Laufen 2× per week']);
    // The empty category offers to be filled.
    expect(element.textContent).toContain('No habits yet.');
  });

  it('shows the ticked days pressed, today marked, and the days to come locked', () => {
    const element = createFixture().nativeElement as HTMLElement;

    const cells = Array.from(element.querySelectorAll<HTMLButtonElement>('button[aria-pressed]')).slice(0, 7);
    expect(cells.map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'true',
      'false',
      'false',
      'true',
      'false',
      'false',
      'false',
    ]);
    expect(cells.map((button) => button.disabled)).toEqual([false, false, false, false, false, true, true]);
    expect(cells[0].getAttribute('aria-label')).toBe('Multivitamin, Monday, October 5, 2026');
    expect(element.querySelector('th[aria-current="date"]')?.textContent).toContain('9.');
  });

  it('shows the week against its target and the streak in days or weeks', () => {
    const element = createFixture().nativeElement as HTMLElement;

    const rows = Array.from(element.querySelectorAll('tbody tr')).filter((row) => row.querySelector('th[scope="row"]'));
    expect(rows[0].textContent).toContain('2/5');
    expect(rows[0].textContent).toContain('1 day');
    expect(rows[1].textContent).toContain('2/2');
    expect(rows[1].textContent).toContain('5 weeks');
  });

  it('asks for a cell to flip, and shows a pending flip before the server answered', () => {
    const fixture = createFixture();
    const element = fixture.nativeElement as HTMLElement;
    const toggles: HabitDayToggle[] = [];
    fixture.componentInstance.dayToggle.subscribe((toggle) => toggles.push(toggle));

    cell(element, 'Multivitamin, Tuesday').click();
    cell(element, 'Multivitamin, Monday').click();
    // A day still to come cannot be clicked.
    cell(element, 'Multivitamin, Saturday').click();

    expect(toggles.map(({ habit, day, checked }) => [habit.id, day, checked])).toEqual([
      ['h1', '2026-10-06', true],
      ['h1', '2026-10-05', false],
    ]);

    fixture.componentRef.setInput('pending', new Map([['h1|2026-10-06', true]]));
    fixture.detectChanges();
    expect(cell(element, 'Multivitamin, Tuesday').getAttribute('aria-pressed')).toBe('true');
  });

  it('passes the row and heading actions on', () => {
    const fixture = createFixture();
    const element = fixture.nativeElement as HTMLElement;
    const events: string[] = [];
    const table = fixture.componentInstance;
    table.editHabit.subscribe((habit) => events.push(`edit ${habit.name}`));
    table.archiveHabit.subscribe((habit) => events.push(`archive ${habit.name}`));
    table.deleteHabit.subscribe((habit) => events.push(`delete ${habit.name}`));
    table.renameCategory.subscribe((category) => events.push(`rename ${category.name}`));
    table.deleteCategory.subscribe((category) => events.push(`delete category ${category.name}`));
    table.addHabit.subscribe((category) => events.push(`add to ${category?.name}`));

    for (const label of ['Edit Multivitamin', 'Archive Laufen', 'Delete Laufen', 'Rename Sport', 'Delete Ernährung']) {
      (element.querySelector(`[aria-label="${label}"]`) as HTMLButtonElement).click();
    }
    (
      Array.from(element.querySelectorAll('button')).find((button) => button.textContent?.includes('Add a habit')) as HTMLButtonElement
    ).click();

    expect(events).toEqual([
      'edit Multivitamin',
      'archive Laufen',
      'delete Laufen',
      'rename Sport',
      'delete category Ernährung',
      'add to Sport',
    ]);
  });
});
