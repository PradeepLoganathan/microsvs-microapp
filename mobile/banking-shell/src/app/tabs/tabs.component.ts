import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  chatbubbleEllipsesOutline,
  chatbubbleEllipses,
  personAddOutline,
  personAdd,
  bulbOutline,
  bulb,
} from 'ionicons/icons';
import { MicroAppService } from '../services/microapp.service';

/**
 * A tab definition — kept in display order. `microappName` matches the entry
 * name in the platform-service manifest; tabs whose micro-app isn't in the
 * active manifest are hidden so the demo console can add/remove tabs live by
 * editing the manifest.
 */
interface ShellTab {
  path: string;
  label: string;
  icon: string;
  microappName: string;
}

const ALL_TABS: ReadonlyArray<ShellTab> = [
  { path: 'home',            label: 'Home',       icon: 'home-outline',                microappName: 'home' },
  { path: 'statements',      label: 'Statements', icon: 'receipt-outline',             microappName: 'statement-details' },
  { path: 'analysis',        label: 'Analysis',   icon: 'analytics-outline',           microappName: 'statement-analysis' },
  { path: 'advisor',         label: 'Advisor',    icon: 'chatbubble-ellipses-outline', microappName: 'advisor' },
  { path: 'onboarding',      label: 'Onboard',    icon: 'person-add-outline',          microappName: 'onboarding' },
  { path: 'recommendations', label: 'Tips',       icon: 'bulb-outline',                microappName: 'recommendations' },
];

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [CommonModule, IonTabs, IonTabBar, IonTabButton, IonIcon, IonLabel],
  template: `
    <ion-tabs>
      <ion-tab-bar slot="bottom">
        <ion-tab-button *ngFor="let t of visibleTabs" [tab]="t.path">
          <ion-icon [name]="t.icon"></ion-icon>
          <ion-label>{{ t.label }}</ion-label>
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
        --color-selected: var(--mbsb-tab-active, #0f3460);
      }
    `,
  ],
})
export class TabsComponent implements OnInit {
  // Render all tabs initially so the bar isn't empty during the manifest fetch.
  // Filtered down to the manifest's entries once it loads.
  visibleTabs: ShellTab[] = [...ALL_TABS];

  constructor(private microAppService: MicroAppService) {
    addIcons({
      homeOutline, home,
      receiptOutline, receipt,
      analyticsOutline, analytics,
      chatbubbleEllipsesOutline, chatbubbleEllipses,
      personAddOutline, personAdd,
      bulbOutline, bulb,
    });
  }

  async ngOnInit(): Promise<void> {
    try {
      const manifest = await this.microAppService.fetchManifest();
      const present = new Set(manifest.microfrontends.map((m) => m.name));
      this.visibleTabs = ALL_TABS.filter((t) => present.has(t.microappName));
    } catch {
      // Manifest unreachable — leave the full set up so the shell still works.
    }
  }
}
