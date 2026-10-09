import { groupByCategory, HabitWeek, WeekHabit } from './habit';

function aHabit(overrides: Partial<WeekHabit>): WeekHabit {
  return {
    id: 'h',
    name: 'Habit',
    categoryId: null,
    frequency: 'DAILY',
    weeklyTarget: null,
    checkedDays: [],
    weekDone: 0,
    weekTarget: 5,
    streak: 0,
    ...overrides,
  };
}

describe('groupByCategory', () => {
  const week: HabitWeek = {
    start: '2026-10-05',
    today: '2026-10-09',
    categories: [
      { id: 'c1', name: 'Ernährung' },
      { id: 'c2', name: 'Schlaf' },
      { id: 'c3', name: 'Sport' },
    ],
    habits: [
      aHabit({ id: 'h1', name: 'Multivitamin', categoryId: 'c1' }),
      aHabit({ id: 'h2', name: 'Laufen', categoryId: 'c3' }),
      aHabit({ id: 'h3', name: 'Lesen', categoryId: null }),
    ],
  };

  it('keeps every category as a heading, empty ones too, with the uncategorised last', () => {
    const groups = groupByCategory(week);

    expect(groups.map((group) => group.category?.name ?? null)).toEqual(['Ernährung', 'Schlaf', 'Sport', null]);
    expect(groups[0].habits.map((habit) => habit.name)).toEqual(['Multivitamin']);
    expect(groups[1].habits).toEqual([]);
    expect(groups[3].habits.map((habit) => habit.name)).toEqual(['Lesen']);
  });

  it('leaves the uncategorised heading out when every habit has a category', () => {
    const groups = groupByCategory({ ...week, habits: week.habits.slice(0, 2) });

    expect(groups.map((group) => group.category?.name ?? null)).toEqual(['Ernährung', 'Schlaf', 'Sport']);
  });
});
