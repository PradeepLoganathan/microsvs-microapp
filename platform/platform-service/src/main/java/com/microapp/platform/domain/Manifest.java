package com.microapp.platform.domain;

import java.util.List;

/**
 * The full micro-frontend manifest — stored as state in the ManifestEntity.
 */
public record Manifest(
    String manifestVersion,
    String channel,
    List<ManifestEntry> microfrontends
) {

  public record ManifestEntry(
      String name,
      String version,
      String remoteEntry,
      String exposedModule,
      String elementTag
  ) {}
}
