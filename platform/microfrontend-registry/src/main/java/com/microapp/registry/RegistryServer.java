package com.microapp.registry;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegistryServer {

    private static final int PORT = 8079;

    public static void main(String[] args) throws IOException {
        // Resolve manifest.json relative to the project root
        Path manifestPath = resolveManifest();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/microfrontends/manifest", exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String manifest = Files.readString(manifestPath);
                byte[] bytes = manifest.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException e) {
                String error = "{\"error\": \"Manifest not found: " + e.getMessage() + "\"}";
                byte[] bytes = error.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
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
        System.out.println("Microfrontend Registry started on port " + PORT);
        System.out.println("Manifest endpoint: http://localhost:" + PORT + "/microfrontends/manifest?channel=demo");
        System.out.println("Manifest file: " + manifestPath.toAbsolutePath());
    }

    private static Path resolveManifest() {
        // Try multiple locations
        Path[] candidates = {
            Path.of("manifest.json"),
            Path.of("platform/microfrontend-registry/manifest.json"),
            Path.of(System.getProperty("user.dir"), "manifest.json")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        // Default to project root
        return candidates[0];
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
