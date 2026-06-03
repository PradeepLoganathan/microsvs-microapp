package com.microapp.platform.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import com.microapp.platform.application.ManifestEntity;
import com.microapp.platform.domain.Manifest;
import com.microapp.platform.domain.Manifest.ManifestEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * HTTP endpoint for the micro-frontend manifest registry.
 *
 * GET  /microfrontends/manifest/{channel}  — fetch current manifest
 * PUT  /microfrontends/manifest/{channel}  — update manifest (version swap)
 * POST /microfrontends/seed                — seed initial manifest data
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/microfrontends")
public class ManifestEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(ManifestEndpoint.class);
  private static final String DEFAULT_CHANNEL = "demo";

  private final ComponentClient componentClient;

  public ManifestEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /**
   * Returns the current manifest for a channel.
   * This is the endpoint the shell app calls to discover micro-apps.
   *
   * Example: GET /microfrontends/manifest/demo
   */
  @Get("/manifest/{channel}")
  public Manifest getManifest(String channel) {
    return componentClient
        .forEventSourcedEntity(channel)
        .method(ManifestEntity::getManifest)
        .invoke();
  }

  /**
   * Updates the manifest for a channel.
   * Used for live version swaps (e.g., switching statement-analysis from v1 to v2).
   *
   * Example: PUT /microfrontends/manifest/demo
   */
  @Put("/manifest/{channel}")
  public HttpResponse updateManifest(String channel, Manifest manifest) {
    logger.info("Updating manifest for channel '{}'", channel);
    componentClient
        .forEventSourcedEntity(channel)
        .method(ManifestEntity::update)
        .invoke(manifest);
    return HttpResponses.ok();
  }

  /**
   * Seeds the default manifest plus per-persona variants. Bundle URLs are relative
   * so they work on any deployed hostname. ManifestEntity.create is idempotent —
   * re-seeding skips channels that already exist.
   *
   * Channels seeded:
   *   demo                    — baseline / existing customer migrating to digital
   *   demo-digital-native     — young mobile-first persona (currently same as demo;
   *                             reserved for future persona-specific variants)
   *   demo-mass-affluent      — wealth-focused persona; pinned to statement-analysis v2
   *                             so Top Merchants is visible on the Analysis tab
   */
  @Post("/seed")
  public HttpResponse seed() {
    logger.info("Seeding manifests (3 channels: demo, demo-digital-native, demo-mass-affluent)...");

    seedChannel(DEFAULT_CHANNEL, "1.0.0");
    seedChannel("demo-digital-native", "1.0.0");
    seedChannel("demo-mass-affluent", "2.0.0");

    logger.info("Manifest seeding complete");
    return HttpResponses.ok();
  }

  private void seedChannel(String channel, String statementAnalysisVersion) {
    var manifest = new Manifest(
        "2026-02-20T00:00:00Z",
        channel,
        List.of(
            new ManifestEntry("home", "1.0.0",
                "/bundles/home/1.0.0/main.js", "./Module", "mf-home"),
            new ManifestEntry("statement-details", "1.0.0",
                "/bundles/statement-details/1.0.0/main.js", "./Module", "mf-statement-details"),
            new ManifestEntry("statement-analysis", statementAnalysisVersion,
                "/bundles/statement-analysis/" + statementAnalysisVersion + "/main.js",
                "./Module", "mf-statement-analysis"),
            new ManifestEntry("recommendations", "1.0.0",
                "/bundles/recommendations/1.0.0/main.js", "./Module", "mf-recommendations"),
            new ManifestEntry("advisor", "1.0.0",
                "/bundles/advisor/1.0.0/main.js", "./Module", "mf-advisor"),
            new ManifestEntry("onboarding", "1.0.0",
                "/bundles/onboarding/1.0.0/main.js", "./Module", "mf-onboarding"),
            new ManifestEntry("prelogin", "1.0.0",
                "/bundles/prelogin/1.0.0/main.js", "./Module", "mf-prelogin"),
            new ManifestEntry("pay", "1.0.0",
                "/bundles/pay/1.0.0/main.js", "./Module", "mf-pay")));

    componentClient
        .forEventSourcedEntity(channel)
        .method(ManifestEntity::create)
        .invoke(manifest);
  }
}
