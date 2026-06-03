import { Component, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonButtons,
  IonButton,
  IonIcon,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBack } from 'ionicons/icons';
import { MicroAppLoaderComponent } from '../microapp-loader/microapp-loader.component';

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonButtons,
    IonButton,
    IonIcon,
    MicroAppLoaderComponent,
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-button (click)="goBack()" aria-label="Back to home">
            <ion-icon slot="icon-only" name="arrow-back"></ion-icon>
          </ion-button>
        </ion-buttons>
        <ion-title>Open an account</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <app-microapp-loader microappName="onboarding"></app-microapp-loader>
    </ion-content>
  `,
  styles: [
    `
      ion-header ion-toolbar {
        --background: var(--mbsb-toolbar-bg, #1a1a2e);
        --color: var(--mbsb-toolbar-fg, #ffffff);
      }
      ion-content {
        --background: #f4f6f9;
      }
    `,
  ],
})
export class OnboardingComponent {
  constructor(private router: Router) {
    addIcons({ arrowBack });
  }

  goBack(): void {
    this.router.navigate(['/app/home']);
  }
}
