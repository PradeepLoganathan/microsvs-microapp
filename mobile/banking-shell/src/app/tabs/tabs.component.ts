import { Component } from '@angular/core';
import {
  IonTabs,
  IonTabBar,
  IonTabButton,
  IonIcon,
  IonLabel,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  homeOutline,
  home,
  receiptOutline,
  receipt,
  analyticsOutline,
  analytics,
  bulbOutline,
  bulb,
} from 'ionicons/icons';

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [IonTabs, IonTabBar, IonTabButton, IonIcon, IonLabel],
  template: `
    <ion-tabs>
      <ion-tab-bar slot="bottom">
        <ion-tab-button tab="home">
          <ion-icon name="home-outline"></ion-icon>
          <ion-label>Home</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="statements">
          <ion-icon name="receipt-outline"></ion-icon>
          <ion-label>Statements</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="analysis">
          <ion-icon name="analytics-outline"></ion-icon>
          <ion-label>Analysis</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="recommendations">
          <ion-icon name="bulb-outline"></ion-icon>
          <ion-label>Tips</ion-label>
        </ion-tab-button>
      </ion-tab-bar>
    </ion-tabs>
  `,
  styles: [
    `
      ion-tab-bar {
        --background: #ffffff;
        --border: 1px solid #e0e0e0;
        padding-bottom: env(safe-area-inset-bottom, 0);
      }

      ion-tab-button {
        --color: #8c8c8c;
        --color-selected: #0f3460;
      }
    `,
  ],
})
export class TabsComponent {
  constructor() {
    addIcons({
      homeOutline,
      home,
      receiptOutline,
      receipt,
      analyticsOutline,
      analytics,
      bulbOutline,
      bulb,
    });
  }
}
