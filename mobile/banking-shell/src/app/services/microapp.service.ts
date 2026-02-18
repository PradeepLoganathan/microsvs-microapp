import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * Represents a single micro-frontend entry from the registry manifest.
 */
export interface MicroAppManifestEntry {
  name: string;
  version: string;
  remoteEntry: string;
  elementTag: string;
  description?: string;
  metadata?: Record<string, unknown>;
}

/**
 * The full manifest response from the micro-frontend registry.
 */
export interface MicroAppManifest {
  channel: string;
  microfrontends: MicroAppManifestEntry[];
  timestamp?: string;
}

/**
 * Tracks the loading state of each micro-app script.
 */
interface ScriptLoadState {
  loaded: boolean;
  loading: Promise<void> | null;
  error: string | null;
}

const MANIFEST_URL =
  'http://localhost:8079/microfrontends/manifest?channel=demo';

@Injectable({
  providedIn: 'root',
})
export class MicroAppService {
  private manifest: MicroAppManifest | null = null;
  private manifestLoading: Promise<MicroAppManifest> | null = null;
  private scriptStates = new Map<string, ScriptLoadState>();

  constructor(private http: HttpClient) {}

  /**
   * Fetches the micro-frontend manifest from the registry.
   * Re-fetches on each call to pick up version changes, but returns
   * a cached promise if a fetch is already in-flight.
   */
  async fetchManifest(forceRefresh = false): Promise<MicroAppManifest> {
    if (this.manifest && !forceRefresh) {
      return this.manifest;
    }

    if (this.manifestLoading && !forceRefresh) {
      return this.manifestLoading;
    }

    this.manifestLoading = this.loadManifest();

    try {
      this.manifest = await this.manifestLoading;
      return this.manifest;
    } finally {
      this.manifestLoading = null;
    }
  }

  /**
   * Returns the manifest entry for a given micro-app name.
   * Fetches the manifest if not yet loaded.
   */
  async getManifestEntry(
    name: string
  ): Promise<MicroAppManifestEntry | undefined> {
    const manifest = await this.fetchManifest(true);
    return manifest.microfrontends.find((mf) => mf.name === name);
  }

  /**
   * Loads the remote entry script for a micro-app.
   * Prevents duplicate loading of the same script URL.
   * Waits for the custom element to be defined before resolving.
   */
  async loadMicroApp(entry: MicroAppManifestEntry): Promise<void> {
    const existing = this.scriptStates.get(entry.name);

    // If already loaded successfully, just verify the element is defined
    if (existing?.loaded) {
      await this.waitForElement(entry.elementTag);
      return;
    }

    // If currently loading, wait for it
    if (existing?.loading) {
      await existing.loading;
      return;
    }

    // Start loading
    const loadPromise = this.injectScript(entry);
    this.scriptStates.set(entry.name, {
      loaded: false,
      loading: loadPromise,
      error: null,
    });

    try {
      await loadPromise;
      this.scriptStates.set(entry.name, {
        loaded: true,
        loading: null,
        error: null,
      });
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : 'Failed to load micro-app';
      this.scriptStates.set(entry.name, {
        loaded: false,
        loading: null,
        error: errorMsg,
      });
      throw err;
    }
  }

  /**
   * Injects a <script> tag for the remote entry URL and waits for:
   * 1. The script to load
   * 2. The custom element to be registered
   */
  private injectScript(entry: MicroAppManifestEntry): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      // Check if script tag already exists in DOM
      const existingScript = document.querySelector(
        `script[data-microapp="${entry.name}"]`
      );
      if (existingScript) {
        this.waitForElement(entry.elementTag).then(resolve).catch(reject);
        return;
      }

      const script = document.createElement('script');
      script.src = entry.remoteEntry;
      script.type = 'text/javascript';
      script.setAttribute('data-microapp', entry.name);
      script.setAttribute('data-version', entry.version);

      script.onload = () => {
        this.waitForElement(entry.elementTag)
          .then(resolve)
          .catch(reject);
      };

      script.onerror = () => {
        // Clean up the failed script tag
        script.remove();
        reject(
          new Error(
            `Failed to load script for "${entry.name}" from ${entry.remoteEntry}`
          )
        );
      };

      document.head.appendChild(script);
    });
  }

  /**
   * Waits for a custom element to be defined in the CustomElementRegistry.
   * Times out after 10 seconds.
   */
  private waitForElement(tagName: string): Promise<void> {
    if (customElements.get(tagName)) {
      return Promise.resolve();
    }

    return new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(
          new Error(
            `Timed out waiting for custom element <${tagName}> to be defined`
          )
        );
      }, 10000);

      customElements
        .whenDefined(tagName)
        .then(() => {
          clearTimeout(timeout);
          resolve();
        })
        .catch((err) => {
          clearTimeout(timeout);
          reject(err);
        });
    });
  }

  /**
   * Internal: performs the actual HTTP fetch of the manifest.
   */
  private async loadManifest(): Promise<MicroAppManifest> {
    try {
      const response = await firstValueFrom(
        this.http.get<MicroAppManifest>(MANIFEST_URL)
      );
      console.log(
        '[MicroAppService] Manifest loaded:',
        response.microfrontends.length,
        'entries'
      );
      return response;
    } catch (err) {
      console.error('[MicroAppService] Failed to fetch manifest:', err);
      throw new Error(
        'Unable to load micro-frontend manifest. Please check your connection.'
      );
    }
  }
}
