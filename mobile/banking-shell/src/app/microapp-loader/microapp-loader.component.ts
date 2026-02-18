import {
  Component,
  Input,
  OnInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  CUSTOM_ELEMENTS_SCHEMA,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  IonSpinner,
  IonButton,
  IonIcon,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { alertCircleOutline, refreshOutline } from 'ionicons/icons';
import {
  MicroAppService,
  MicroAppManifestEntry,
} from '../services/microapp.service';

type LoadingState = 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-microapp-loader',
  standalone: true,
  imports: [CommonModule, IonSpinner, IonButton, IonIcon],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <!-- Loading State -->
    @if (state === 'loading') {
      <div class="microapp-loading">
        <ion-spinner name="crescent" color="secondary"></ion-spinner>
        <p class="loading-text">Loading {{ microappName }}...</p>
      </div>
    }

    <!-- Error State -->
    @if (state === 'error') {
      <div class="microapp-error">
        <ion-icon name="alert-circle-outline"></ion-icon>
        <p class="error-title">Unable to Load</p>
        <p class="error-message">{{ errorMessage }}</p>
        <ion-button fill="outline" size="small" (click)="retry()">
          <ion-icon name="refresh-outline" slot="start"></ion-icon>
          Retry
        </ion-button>
      </div>
    }

    <!-- Micro-App Container -->
    <div
      #microappHost
      class="microapp-host"
      [style.display]="state === 'loaded' ? 'block' : 'none'"
    ></div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        min-height: 200px;
      }

      .microapp-host {
        width: 100%;
      }

      .microapp-loading {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 64px 24px;
        text-align: center;
      }

      .microapp-loading ion-spinner {
        width: 48px;
        height: 48px;
        margin-bottom: 16px;
      }

      .microapp-loading .loading-text {
        color: var(--ion-color-medium, #92949c);
        font-size: 0.9rem;
      }

      .microapp-error {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 64px 24px;
        text-align: center;
      }

      .microapp-error ion-icon {
        font-size: 48px;
        color: var(--ion-color-danger, #eb445a);
        margin-bottom: 16px;
      }

      .microapp-error .error-title {
        font-size: 1.1rem;
        font-weight: 600;
        color: var(--ion-color-dark, #222428);
        margin-bottom: 8px;
      }

      .microapp-error .error-message {
        font-size: 0.85rem;
        color: var(--ion-color-medium, #92949c);
        margin-bottom: 20px;
        max-width: 280px;
        line-height: 1.4;
      }
    `,
  ],
})
export class MicroAppLoaderComponent implements OnInit, OnDestroy {
  @Input({ required: true }) microappName!: string;

  @ViewChild('microappHost', { static: true }) hostRef!: ElementRef<HTMLDivElement>;

  state: LoadingState = 'loading';
  errorMessage = '';

  private manifestEntry: MicroAppManifestEntry | null = null;

  constructor(
    private microAppService: MicroAppService,
    private cdr: ChangeDetectorRef
  ) {
    addIcons({ alertCircleOutline, refreshOutline });
  }

  async ngOnInit(): Promise<void> {
    await this.loadMicroApp();
  }

  ngOnDestroy(): void {
    // Clean up the rendered custom element
    const host = this.hostRef?.nativeElement;
    if (host) {
      host.innerHTML = '';
    }
  }

  /**
   * Retry loading the micro-app after a failure.
   */
  async retry(): Promise<void> {
    this.state = 'loading';
    this.errorMessage = '';
    this.cdr.detectChanges();
    await this.loadMicroApp();
  }

  /**
   * Orchestrates the full loading sequence:
   * 1. Fetch manifest entry
   * 2. Load remote script
   * 3. Render the custom element
   */
  private async loadMicroApp(): Promise<void> {
    try {
      // Step 1: Get the manifest entry for this micro-app
      const entry = await this.microAppService.getManifestEntry(
        this.microappName
      );

      if (!entry) {
        throw new Error(
          `Micro-app "${this.microappName}" not found in manifest`
        );
      }

      this.manifestEntry = entry;

      // Step 2: Load the remote entry script and wait for custom element
      await this.microAppService.loadMicroApp(entry);

      // Step 3: Render the custom element into the host container
      this.renderElement(entry.elementTag);

      this.state = 'loaded';
      this.cdr.detectChanges();

      console.log(
        `[MicroAppLoader] Successfully loaded <${entry.elementTag}> v${entry.version}`
      );
    } catch (err) {
      this.state = 'error';
      this.errorMessage =
        err instanceof Error
          ? err.message
          : 'An unexpected error occurred while loading the micro-app.';
      this.cdr.detectChanges();

      console.error(
        `[MicroAppLoader] Failed to load "${this.microappName}":`,
        err
      );
    }
  }

  /**
   * Creates the custom element and appends it to the host container.
   * Passes the account ID as an attribute for context.
   */
  private renderElement(tagName: string): void {
    const host = this.hostRef.nativeElement;

    // Clear any previous content
    host.innerHTML = '';

    // Create the custom element
    const element = document.createElement(tagName);
    element.setAttribute('account-id', 'acc-1001');

    host.appendChild(element);
  }
}
