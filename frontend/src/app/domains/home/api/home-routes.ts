import { Routes } from '@angular/router';

export const homeRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-start/start-page').then((m) => m.StartPage),
  },
];
