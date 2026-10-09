import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { HabitWeek } from '../model/habit';
import { HabitsService } from './habits-service';

const week: HabitWeek = { start: '2026-10-05', today: '2026-10-09', categories: [], habits: [] };

describe('HabitsService', () => {
  let service: HabitsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HabitsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  async function settle(): Promise<void> {
    TestBed.tick();
    await TestBed.inject(ApplicationRef).whenStable();
  }

  it('reads the current week, or the week asked for', async () => {
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);
    await settle();
    expect(service.week.value()?.start).toBe('2026-10-05');

    service.weekStart.set('2026-09-28');
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week?start=2026-09-28').flush({ ...week, start: '2026-09-28' });
    await settle();
    expect(service.week.value()?.start).toBe('2026-09-28');
  });

  it('ticks and unticks a day, then reads the week again', async () => {
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);
    await settle();

    const checking = service.check('h1', '2026-10-08');
    const put = httpTesting.expectOne('/api/habits/h1/checks/2026-10-08');
    expect(put.request.method).toBe('PUT');
    put.flush(null);
    await checking;
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);

    await settle();

    const unchecking = service.uncheck('h1', '2026-10-08');
    const deletion = httpTesting.expectOne('/api/habits/h1/checks/2026-10-08');
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
    await unchecking;
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);
    httpTesting.verify();
  });

  it('reads the archive only once somebody asks for it', async () => {
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);
    httpTesting.expectNone('/api/habits/archived');

    service.loadArchive();
    TestBed.tick();
    httpTesting.expectOne('/api/habits/archived').flush([]);
    await settle();
    expect(service.archived.value()).toEqual([]);
  });

  it('sends a new habit and a category the way the backend expects them', async () => {
    TestBed.tick();
    httpTesting.expectOne('/api/habits/week').flush(week);

    const creating = service.createHabit({ name: 'Laufen', categoryId: 'c1', frequency: 'WEEKLY', weeklyTarget: 2 });
    const post = httpTesting.expectOne('/api/habits');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ name: 'Laufen', categoryId: 'c1', frequency: 'WEEKLY', weeklyTarget: 2 });
    post.flush({});
    await creating;

    const naming = service.createCategory('Schlaf');
    const categoryPost = httpTesting.expectOne('/api/habit-categories');
    expect(categoryPost.request.body).toEqual({ name: 'Schlaf' });
    categoryPost.flush({ id: 'c9', name: 'Schlaf' });
    expect(await naming).toEqual({ id: 'c9', name: 'Schlaf' });
  });
});
