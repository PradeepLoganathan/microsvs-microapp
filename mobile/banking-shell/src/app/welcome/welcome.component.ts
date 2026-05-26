import { Component, CUSTOM_ELEMENTS_SCHEMA, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { IonContent, IonButton } from '@ionic/angular/standalone';
import { MicroAppLoaderComponent } from '../microapp-loader/microapp-loader.component';

/**
 * Pre-login landing (cold open). Hosts the `prelogin` micro-app and enters the
 * tabbed app once the visitor becomes a customer — the micro-app dispatches a
 * bubbling `enter-app` CustomEvent which we catch here. "Skip" goes straight in,
 * preserving the existing acc-1001 demo path.
 */
@Component({
  selector: 'app-welcome',
  standalone: true,
  imports: [IonContent, IonButton, MicroAppLoaderComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ion-content>
      <app-microapp-loader microappName="prelogin"></app-microapp-loader>
      <div class="skip">
        <ion-button fill="clear" size="small" (click)="enter()">
          Skip — I already have an account
        </ion-button>
      </div>
    </ion-content>
  `,
  styles: [
    `
      ion-content {
        --background: #f4f6f9;
      }
      .skip {
        text-align: center;
        padding: 8px 0 16px;
      }
      .skip ion-button {
        --color: #8a90a6;
        font-size: 12.5px;
      }
    `,
  ],
})
export class WelcomeComponent {
  constructor(private router: Router) {}

  /** Bubbles up from the <mf-prelogin> element when the customer is fully onboarded. */
  @HostListener('enter-app')
  enter(): void {
    this.router.navigateByUrl('/app/home');
  }
}
