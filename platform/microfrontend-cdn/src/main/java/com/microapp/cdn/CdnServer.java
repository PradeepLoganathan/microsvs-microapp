package com.microapp.cdn;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CdnServer {

    private static final int PORT = 8090;
    private static final Map<String, String> MIME_TYPES = Map.of(
        "js", "application/javascript",
        "css", "text/css",
        "html", "text/html",
        "json", "application/json",
        "map", "application/json",
        "svg", "image/svg+xml",
        "png", "image/png",
        "woff2", "font/woff2"
    );

    public static void main(String[] args) throws IOException {
        Path wwwRoot = resolveWwwRoot();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                sendText(exchange, 200, "Microfrontend CDN - serving from: " + wwwRoot.toAbsolutePath());
                return;
            }

            // Strip leading slash and resolve
            Path filePath = wwwRoot.resolve(path.substring(1)).normalize();

            // Security: ensure file is within www root
            if (!filePath.startsWith(wwwRoot)) {
                sendText(exchange, 403, "Forbidden");
                return;
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendText(exchange, 404, "Not found: " + path);
                return;
            }

            byte[] content = Files.readAllBytes(filePath);
            String ext = getExtension(filePath.getFileName().toString());
            String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        });

        server.createContext("/health", exchange -> {
            addCorsHeaders(exchange);
            byte[] response = "{\"status\":\"UP\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Microfrontend CDN started on port " + PORT);
        System.out.println("Serving files from: " + wwwRoot.toAbsolutePath());
    }

    private static Path resolveWwwRoot() {
        Path[] candidates = {
            Path.of("www"),
            Path.of("platform/microfrontend-cdn/www"),
            Path.of(System.getProperty("user.dir"), "www")
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) return p;
        }
        // Create default
        Path def = candidates[0];
        try { Files.createDirectories(def); } catch (IOException ignored) {}
        return def;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private static void sendText(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
