package com.newgen.los.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// splits a .sql file on "-- name:" lines and turns :params into ?
public final class NamedSql {

    private final String sql;
    private final List<String> parameters;

    private NamedSql(String sql, List<String> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public String sql() {
        return sql;
    }

    public List<String> parameters() {
        return parameters;
    }

    public static Map<String, NamedSql> load(String classpathResource) {
        try (InputStream in = NamedSql.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + classpathResource);
            }
            return parseFile(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + classpathResource, e);
        }
    }

    static Map<String, NamedSql> parseFile(String content) {
        Map<String, NamedSql> queries = new LinkedHashMap<>();
        String currentName = null;
        StringBuilder body = new StringBuilder();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-- name:")) {
                if (currentName != null) {
                    queries.put(currentName, parse(body.toString()));
                }
                currentName = trimmed.substring("-- name:".length()).trim();
                body.setLength(0);
            } else if (currentName != null && !trimmed.startsWith("--")) {
                body.append(line).append('\n');
            }
        }
        if (currentName != null) {
            queries.put(currentName, parse(body.toString()));
        }
        return Collections.unmodifiableMap(queries);
    }

    static NamedSql parse(String raw) {
        String text = raw.trim();
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1);
        }
        StringBuilder out = new StringBuilder();
        List<String> params = new ArrayList<>();
        boolean inQuote = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                out.append(c);
                i++;
            } else if (!inQuote && c == ':' && i + 1 < text.length() && Character.isLetter(text.charAt(i + 1))) {
                int j = i + 1;
                while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                    j++;
                }
                params.add(text.substring(i + 1, j));
                out.append('?');
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return new NamedSql(out.toString(), List.copyOf(params));
    }
}
