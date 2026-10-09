import { Routes } from '@angular/router';

export const habitsRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-week/habits-page').then((m) => m.HabitsPage),
  },
];
