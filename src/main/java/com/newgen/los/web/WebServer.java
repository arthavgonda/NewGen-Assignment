package com.newgen.los.web;

import com.newgen.los.AppContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "woff2", "font/woff2",
            "txt", "text/plain; charset=utf-8");

    private final HttpServer server;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    public WebServer(AppContext ctx, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        ApiHandler api = new ApiHandler(ctx);
        server.createContext("/api/", exchange -> {
            try (exchange) {
                api.handle(exchange, exchange.getRequestURI().getPath().substring("/api/".length()));
            }
        });
        server.createContext("/", exchange -> {
            try (exchange) {
                serveStatic(exchange);
            }
        });
        server.setExecutor(pool);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
        pool.shutdownNow();
    }

    private void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            Responses.send(exchange, 400, "text/plain", "Bad request".getBytes());
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/web" + path)) {
            if (in == null) {
                Responses.send(exchange, 404, "text/plain", "Not found".getBytes());
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            if (ext.equals("woff2")) {
                exchange.getResponseHeaders().set("Cache-Control", "max-age=86400");
            }
            Responses.send(exchange, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }
}
