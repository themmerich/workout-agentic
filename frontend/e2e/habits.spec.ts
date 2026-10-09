import { expect, Page, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked, here with a little state so the page
// sees its own changes come back. The assertions use the German texts, de being the default.
const user = {
  username: 'admin',
  displayName: 'Anna Admin',
  role: 'admin',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
};

/** The mocked server's week: Monday 5 to Sunday 11 October 2026, today a Friday. */
const THIS_MONDAY = '2026-10-05';
const TODAY = '2026-10-09';

type MockHabit = { id: string; name: string; categoryId: string | null; frequency: 'DAILY' | 'WEEKLY'; weeklyTarget: number | null };

async function mockHabitsApi(page: Page) {
  const habits: MockHabit[] = [];
  const checks = new Set<string>();

  await page.route('**/api/habits/week**', (route) => {
    const start = new URL(route.request().url()).searchParams.get('start') ?? THIS_MONDAY;
    return route.fulfill({
      json: {
        start,
        today: TODAY,
        categories: [],
        habits: habits.map((habit) => {
          const checkedDays = [...checks]
            .filter((key) => key.startsWith(`${habit.id}|`))
            .map((key) => key.split('|')[1])
            .sort();
          return { ...habit, checkedDays, weekDone: checkedDays.length, weekTarget: 5, streak: 0 };
        }),
      },
    });
  });
  await page.route('**/api/habits', (route) => {
    const body = route.request().postDataJSON() as Omit<MockHabit, 'id'>;
    const habit = { ...body, id: `h${habits.length + 1}` };
    habits.push(habit);
    return route.fulfill({ status: 201, json: { ...habit, categoryName: null, archivedAt: null } });
  });
  await page.route('**/api/habits/*/checks/*', (route) => {
    const [, habitId, , day] = new URL(route.request().url()).pathname.split('/').slice(2);
    const key = `${habitId}|${day}`;
    if (route.request().method() === 'PUT') {
      checks.add(key);
    } else {
      checks.delete(key);
    }
    return route.fulfill({ status: 204, body: '' });
  });
  return { habits, checks };
}

test.describe('Habits', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: user }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
  });

  test('adds a first habit, ticks a day of it, and turns back a week', async ({ page }) => {
    const server = await mockHabitsApi(page);

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Gewohnheiten', level: 1 })).toBeVisible();
    await expect(page.getByRole('heading', { name: /KW 41/ })).toBeVisible();

    await page.getByRole('button', { name: 'Erste Gewohnheit anlegen' }).click();
    await page.getByLabel('Name').fill('Multivitamin');
    await page.getByRole('dialog').getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Gewohnheit angelegt.')).toBeVisible();
    expect(server.habits).toEqual([{ id: 'h1', name: 'Multivitamin', categoryId: null, frequency: 'DAILY', weeklyTarget: null }]);
    await expect(page.getByRole('rowheader', { name: /Multivitamin/ })).toBeVisible();

    const tuesday = page.getByRole('button', { name: 'Multivitamin, Dienstag, 6. Oktober 2026' });
    await expect(tuesday).toHaveAttribute('aria-pressed', 'false');
    await tuesday.click();
    await expect(tuesday).toHaveAttribute('aria-pressed', 'true');
    expect([...server.checks]).toEqual(['h1|2026-10-06']);
    // Saturday is still to come.
    await expect(page.getByRole('button', { name: 'Multivitamin, Samstag, 10. Oktober 2026' })).toBeDisabled();

    await page.getByRole('button', { name: 'Vorige Woche' }).click();
    await expect(page).toHaveURL(/\?week=2026-09-28$/);
    await expect(page.getByRole('heading', { name: /KW 40/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Multivitamin, Dienstag, 29. September 2026' })).toHaveAttribute('aria-pressed', 'false');
  });

  test('keeps the week shown across a reload', async ({ page }) => {
    await mockHabitsApi(page);

    await page.goto('/?week=2026-09-30');

    await expect(page.getByRole('heading', { name: /KW 40/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Diese Woche' })).toBeEnabled();
  });
});
