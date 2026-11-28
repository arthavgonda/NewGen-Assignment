package com.newgen.los.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Responses {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private Responses() {
    }

    static void json(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    static void error(HttpExchange exchange, int status, String message, List<String> details) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("details", details);
        json(exchange, status, body);
    }

    static void file(HttpExchange exchange, Path file, String contentType, boolean download) throws IOException {
        if (download) {
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + file.getFileName() + "\"");
        }
        send(exchange, 200, contentType, Files.readAllBytes(file));
    }

    static void send(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (!exchange.getResponseHeaders().containsKey("Cache-Control")) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
