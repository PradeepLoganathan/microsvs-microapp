package com.microapp.platform.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.CacheControl;
import akka.http.javadsl.model.headers.CacheDirectives;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves pre-built micro-app JavaScript bundles from classpath resources.
 *
 * Bundles are stored in src/main/resources/www/{name}/{version}/main.js
 * and served at GET /bundles/{name}/{version}/main.js
 *
 * This replaces the standalone CdnServer with an Akka SDK endpoint.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/bundles")
public class BundleEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(BundleEndpoint.class);

  /**
   * Serves a micro-app bundle file from classpath resources.
   *
   * Path: GET /bundles/{name}/{version}/{filename}
   * Example: GET /bundles/statement-details/1.0.0/main.js
   */
  @Get("/{name}/{version}/{filename}")
  public HttpResponse getBundle(String name, String version, String filename) {
    // Validate path segments to prevent directory traversal
    if (containsTraversal(name) || containsTraversal(version) || containsTraversal(filename)) {
      logger.warn("Rejected path traversal attempt: {}/{}/{}", name, version, filename);
      return HttpResponse.create()
          .withStatus(StatusCodes.FORBIDDEN)
          .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Forbidden");
    }

    String resourcePath = "www/" + name + "/" + version + "/" + filename;

    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        logger.debug("Bundle not found: {}", resourcePath);
        return HttpResponse.create()
            .withStatus(StatusCodes.NOT_FOUND)
            .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Not found: " + name + "/" + version + "/" + filename);
      }

      byte[] content = is.readAllBytes();
      String contentType = resolveContentType(filename);

      logger.debug("Serving bundle: {} ({} bytes)", resourcePath, content.length);

      return HttpResponse.create()
          .withStatus(StatusCodes.OK)
          .withEntity(HttpEntities.create(ContentTypes.create(
              akka.http.javadsl.model.MediaTypes.applicationWithFixedCharset(
                  "javascript", akka.http.javadsl.model.HttpCharsets.UTF_8
              )), content))
          .addHeader(CacheControl.create(CacheDirectives.NO_CACHE));
    } catch (IOException e) {
      logger.error("Error reading bundle: {}", resourcePath, e);
      return HttpResponse.create()
          .withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
          .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Internal error");
    }
  }

  private static boolean containsTraversal(String segment) {
    return segment.contains("..") || segment.contains("/") || segment.contains("\\");
  }

  private static String resolveContentType(String filename) {
    if (filename.endsWith(".js")) return "application/javascript";
    if (filename.endsWith(".css")) return "text/css";
    if (filename.endsWith(".json")) return "application/json";
    if (filename.endsWith(".map")) return "application/json";
    return "application/octet-stream";
  }
}
