import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { PersonaService } from './services/persona.service';

interface NavigateDetail {
  type: 'TABUNG' | 'CASA' | 'TAKAFUL' | 'ADVISOR_HUMAN';
  params?: Record<string, string>;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet],
  template: `
    <ion-app>
      <ion-router-outlet></ion-router-outlet>
    </ion-app>
  `,
})
export class AppComponent implements OnInit, OnDestroy {
  constructor(private personaService: PersonaService, private router: Router) {}

  ngOnInit(): void {
    // Make the active persona available to CSS via [data-persona] on <body>
    // so per-persona themes can be added without touching component code.
    const persona = this.personaService.current();
    document.body.setAttribute('data-persona', persona ?? 'default');

    // The advisor micro-app (and any future micro-app) dispatches `mbsb-navigate`
    // with composed:true so the event bubbles out of its Shadow DOM up to here.
    document.addEventListener('mbsb-navigate', this.onMicroAppNavigate);
  }

  ngOnDestroy(): void {
    document.removeEventListener('mbsb-navigate', this.onMicroAppNavigate);
  }

  /**
   * Map a structured action from a micro-app to an Angular route. The shell owns
   * the navigation surface; the micro-app only declares intent. This keeps URLs
   * deterministic and prevents free-form navigation from inside Shadow DOMs.
   */
  private onMicroAppNavigate = (event: Event): void => {
    const detail = (event as CustomEvent<NavigateDetail>).detail;
    if (!detail) return;
    const params = detail.params ?? {};

    switch (detail.type) {
      case 'TABUNG':
        this.router.navigate(['/app/home'], {
          queryParams: {
            openTabung: (params['category'] || 'OTHER').toUpperCase(),
            name: params['name'] || '',
            target: params['targetAmount'] || '',
            date: params['targetDate'] || '',
          },
        });
        break;
      case 'CASA':
        this.router.navigate(['/app/onboarding'], { queryParams: { product: 'casa' } });
        break;
      case 'TAKAFUL':
        this.router.navigate(['/app/onboarding'], {
          queryParams: { product: 'takaful', planId: params['planId'] || '' },
        });
        break;
      case 'ADVISOR_HUMAN':
        // Handoff already logged server-side via requestHumanHandoff. Stay put;
        // the chat bubble already shows the "an advisor will follow up" copy.
        break;
    }
  };
}
