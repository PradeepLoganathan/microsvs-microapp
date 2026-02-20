package com.microapp.platform.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ManifestEvent {

  @TypeName("manifest-created")
  record ManifestCreated(Manifest manifest) implements ManifestEvent {}

  @TypeName("manifest-updated")
  record ManifestUpdated(Manifest manifest) implements ManifestEvent {}
}
