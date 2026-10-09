import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ArchivedHabit, HabitCategory, HabitInput, HabitWeek } from '../model/habit';

/**
 * The signed-in user's habits. The week grid reads one calendar week; every change reloads it, so
 * the progress and the streaks shown are always the server's, never a guess of the page's.
 */
@Service()
export class HabitsService {
  private readonly http = inject(HttpClient);

  /** The week to show, by any of its days; null for the current one. */
  readonly weekStart = signal<string | null>(null);

  readonly week = httpResource<HabitWeek>(() => {
    const start = this.weekStart();
    return start === null ? { url: '/api/habits/week' } : { url: '/api/habits/week', params: { start } };
  });

  /** Read only once somebody opens the archive; until then nobody needs it. */
  private readonly isArchiveWanted = signal(false);
  readonly archived = httpResource<ArchivedHabit[]>(() => (this.isArchiveWanted() ? '/api/habits/archived' : undefined), {
    defaultValue: [],
  });

  loadArchive(): void {
    if (this.isArchiveWanted()) {
      this.archived.reload();
    } else {
      this.isArchiveWanted.set(true);
    }
  }

  async check(habitId: string, day: string): Promise<void> {
    await firstValueFrom(this.http.put<void>(`/api/habits/${habitId}/checks/${day}`, null));
    this.week.reload();
  }

  async uncheck(habitId: string, day: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/habits/${habitId}/checks/${day}`));
    this.week.reload();
  }

  async createHabit(input: HabitInput): Promise<void> {
    await firstValueFrom(this.http.post<unknown>('/api/habits', input));
    this.week.reload();
  }

  async updateHabit(id: string, input: HabitInput): Promise<void> {
    await firstValueFrom(this.http.put<unknown>(`/api/habits/${id}`, input));
    this.week.reload();
  }

  /** Final: the ticks go along. */
  async deleteHabit(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/habits/${id}`));
    this.week.reload();
    this.archived.reload();
  }

  /** Archiving takes a habit out of the grid and keeps its ticks; restoring brings it back. */
  async setArchived(id: string, archived: boolean): Promise<void> {
    await firstValueFrom(this.http.put<unknown>(`/api/habits/${id}/archived`, { archived }));
    this.week.reload();
    this.archived.reload();
  }

  async createCategory(name: string): Promise<HabitCategory> {
    const category = await firstValueFrom(this.http.post<HabitCategory>('/api/habit-categories', { name }));
    this.week.reload();
    return category;
  }

  async renameCategory(id: string, name: string): Promise<void> {
    await firstValueFrom(this.http.put<HabitCategory>(`/api/habit-categories/${id}`, { name }));
    this.week.reload();
  }

  /** The category's habits stay, without one. */
  async deleteCategory(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/habit-categories/${id}`));
    this.week.reload();
  }
}
