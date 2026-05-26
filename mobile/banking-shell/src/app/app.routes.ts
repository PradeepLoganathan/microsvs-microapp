import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./tabs/tabs.component').then((m) => m.TabsComponent),
    children: [
      {
        path: 'home',
        loadComponent: () =>
          import('./home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'statements',
        loadComponent: () =>
          import('./statements/statements.component').then(
            (m) => m.StatementsComponent
          ),
      },
      {
        path: 'analysis',
        loadComponent: () =>
          import('./analysis/analysis.component').then(
            (m) => m.AnalysisComponent
          ),
      },
      {
        path: 'advisor',
        loadComponent: () =>
          import('./advisor/advisor.component').then(
            (m) => m.AdvisorComponent
          ),
      },
      {
        path: 'onboarding',
        loadComponent: () =>
          import('./onboarding/onboarding.component').then(
            (m) => m.OnboardingComponent
          ),
      },
      {
        path: 'recommendations',
        loadComponent: () =>
          import('./recommendations/recommendations.component').then(
            (m) => m.RecommendationsComponent
          ),
      },
      {
        path: '',
        redirectTo: 'home',
        pathMatch: 'full',
      },
    ],
  },
];
