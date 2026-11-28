package com.newgen.los.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

class Request {

    private static final Gson GSON = new Gson();

    private final HttpExchange exchange;
    private final Map<String, String> query;
    private final String[] pathParts;
    private JsonObject body;

    Request(HttpExchange exchange, String path) {
        this.exchange = exchange;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
        String trimmed = path.replaceAll("^/+|/+$", "");
        this.pathParts = trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    String method() {
        return exchange.getRequestMethod();
    }

    String part(int index) {
        return index < pathParts.length ? pathParts[index] : null;
    }

    int parts() {
        return pathParts.length;
    }

    long idPart(int index) {
        String value = part(index);
        if (value == null || !value.matches("\\d+")) {
            throw new HttpError(404, "Not found");
        }
        return Long.parseLong(value);
    }

    String query(String name) {
        String value = query.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    String userCode() {
        String user = exchange.getRequestHeaders().getFirst("X-User-Code");
        return user == null || user.isBlank() ? "WEBUSER" : user.trim();
    }

    JsonObject body() {
        if (body == null) {
            try {
                String text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                body = text.isBlank() ? new JsonObject() : GSON.fromJson(text, JsonObject.class);
                if (body == null) {
                    body = new JsonObject();
                }
            } catch (IOException | JsonParseException e) {
                throw new HttpError(400, "Request body is not valid JSON");
            }
        }
        return body;
    }

    String str(String field) {
        JsonObject b = body();
        if (!b.has(field) || b.get(field).isJsonNull()) {
            return null;
        }
        String value = b.get(field).getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    BigDecimal decimal(String field) {
        String value = str(field);
        try {
            return MoneyUtil.parse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label(field) + " is not a valid amount");
        }
    }

    Integer wholeNumber(String field) {
        String value = str(field);
        if (value == null) {
            return null;
        }
        if (!value.matches("-?\\d+")) {
            throw new IllegalArgumentException(label(field) + " must be a whole number");
        }
        return Integer.parseInt(value);
    }

    LocalDate date(String field) {
        return DateUtil.parse(str(field));
    }

    private static String label(String field) {
        String words = field.replaceAll("([A-Z])", " $1").toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }
}
