import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { PersonaService } from './services/persona.service';
import { ActiveAccountService } from './services/active-account.service';

interface NavigateDetail {
  type: 'TABUNG' | 'CASA' | 'TAKAFUL' | 'ADVISOR_HUMAN' | 'ONBOARD';
  params?: Record<string, string>;
}

/** Payload of `mbsb-select-account`, dispatched when the user taps an account card. */
interface SelectAccountDetail {
  accountId: string;
  customerId?: string;
  productName?: string;
  accountNumber?: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet],
  template: `
    <ion-app>
      <!-- Persona badge: a purely-informational overlay anchoring the audience on
           which customer persona is driving the manifest + theme. pointer-events:none
           so it never intercepts taps on the app beneath it. -->
      <div class="persona-badge" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor">
          <path d="M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5Zm0 2c-4 0-8 2-8 5v1h16v-1c0-3-4-5-8-5Z" />
        </svg>
        <span>Viewing as: {{ personaLabel }}</span>
      </div>
      <ion-router-outlet></ion-router-outlet>
    </ion-app>
  `,
  styles: [
    `
      .persona-badge {
        position: absolute;
        top: 8px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1000;
        pointer-events: none;
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 3px 10px;
        border-radius: 999px;
        font-size: 12px;
        font-weight: 600;
        line-height: 1;
        white-space: nowrap;
        background: var(--mbsb-toolbar-bg, #1a1a2e);
        color: var(--mbsb-toolbar-fg, #ffffff);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
      }
    `,
  ],
})
export class AppComponent implements OnInit, OnDestroy {
  constructor(
    private personaService: PersonaService,
    private activeAccount: ActiveAccountService,
    private router: Router
  ) {}

  /** Friendly persona label for the badge. */
  get personaLabel(): string {
    switch (this.personaService.current()) {
      case 'digital-native':
        return 'Digital Native';
      case 'mass-affluent':
        return 'Mass Affluent';
      default:
        return 'Existing customer';
    }
  }

  ngOnInit(): void {
    // Make the active persona available to CSS via [data-persona] on <body>
    // so per-persona themes can be added without touching component code.
    const persona = this.personaService.current();
    document.body.setAttribute('data-persona', persona ?? 'default');

    // The advisor micro-app (and any future micro-app) dispatches `mbsb-navigate`
    // with composed:true so the event bubbles out of its Shadow DOM up to here.
    document.addEventListener('mbsb-navigate', this.onMicroAppNavigate);

    // Tapping an account card on the home screen switches the active account.
    document.addEventListener('mbsb-select-account', this.onSelectAccount);
  }

  ngOnDestroy(): void {
    document.removeEventListener('mbsb-navigate', this.onMicroAppNavigate);
    document.removeEventListener('mbsb-select-account', this.onSelectAccount);
  }

  /**
   * Switch the account driving the data tabs, then jump to Statements so the
   * change is immediately visible. The shell owns the active-account state; the
   * micro-app only declares which account was chosen.
   */
  private onSelectAccount = (event: Event): void => {
    const detail = (event as CustomEvent<SelectAccountDetail>).detail;
    if (!detail || !detail.accountId) return;

    this.activeAccount.setActive({
      accountId: detail.accountId,
      customerId: detail.customerId || this.activeAccount.current().customerId,
      productName: detail.productName || detail.accountId,
      accountNumber: detail.accountNumber,
    });
    this.router.navigate(['/app/statements']);
  };

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
      case 'ONBOARD':
        // "Open a new account" from Home → onboarding's CASA/Takaful chooser.
        this.router.navigate(['/app/onboarding']);
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
