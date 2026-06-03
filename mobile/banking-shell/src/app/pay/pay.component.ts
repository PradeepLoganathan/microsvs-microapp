import { Component, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
} from '@ionic/angular/standalone';
import { MicroAppLoaderComponent } from '../microapp-loader/microapp-loader.component';

@Component({
  selector: 'app-pay',
  standalone: true,
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    MicroAppLoaderComponent,
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Pay & Transfer</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <app-microapp-loader microappName="pay"></app-microapp-loader>
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
export class PayComponent {}
