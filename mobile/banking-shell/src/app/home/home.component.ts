import { Component, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
} from '@ionic/angular/standalone';
import { MicroAppLoaderComponent } from '../microapp-loader/microapp-loader.component';

@Component({
  selector: 'app-home',
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
        <ion-title>Banking</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <app-microapp-loader microappName="home"></app-microapp-loader>
    </ion-content>
  `,
  styles: [
    `
      ion-header ion-toolbar {
        --background: #1a1a2e;
        --color: #ffffff;
      }
    `,
  ],
})
export class HomeComponent {}
