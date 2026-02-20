package com.microapp.platform.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.microapp.platform.domain.Manifest;
import com.microapp.platform.domain.ManifestEvent;
import com.microapp.platform.domain.ManifestEvent.ManifestCreated;
import com.microapp.platform.domain.ManifestEvent.ManifestUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;
import akka.Done;

/**
 * Event-sourced entity storing the micro-frontend manifest.
 * Entity ID is the channel name (e.g., "demo").
 */
@Component(id = "manifest")
public class ManifestEntity extends EventSourcedEntity<Manifest, ManifestEvent> {

  private static final Logger logger = LoggerFactory.getLogger(ManifestEntity.class);

  /**
   * Returns the current manifest state.
   */
  public ReadOnlyEffect<Manifest> getManifest() {
    if (currentState() == null) {
      return effects().error("No manifest found for channel '" + commandContext().entityId() + "'");
    }
    return effects().reply(currentState());
  }

  /**
   * Creates or seeds the manifest for a channel.
   * Idempotent — if manifest already exists, returns success without changes.
   */
  public Effect<Done> create(Manifest manifest) {
    if (currentState() != null) {
      logger.info("Manifest for channel '{}' already exists, skipping create", commandContext().entityId());
      return effects().reply(done());
    }
    logger.info("Creating manifest for channel '{}' with {} micro-frontends",
        commandContext().entityId(), manifest.microfrontends().size());
    return effects()
        .persist(new ManifestCreated(manifest))
        .thenReply(__ -> done());
  }

  /**
   * Updates the manifest — used for version swaps (e.g., v1 → v2 demo).
   */
  public Effect<Done> update(Manifest manifest) {
    if (currentState() == null) {
      return effects().error("No manifest found for channel '" + commandContext().entityId() + "'. Seed it first.");
    }
    logger.info("Updating manifest for channel '{}'", commandContext().entityId());
    return effects()
        .persist(new ManifestUpdated(manifest))
        .thenReply(__ -> done());
  }

  @Override
  public Manifest applyEvent(ManifestEvent event) {
    return switch (event) {
      case ManifestCreated created -> created.manifest();
      case ManifestUpdated updated -> updated.manifest();
    };
  }
}
