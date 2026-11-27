package com.newgen.los.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Display {

    private Display() {
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return MoneyUtil.format((BigDecimal) value);
        }
        if (value instanceof LocalDateTime) {
            return DateUtil.format((LocalDateTime) value);
        }
        if (value instanceof LocalDate) {
            return DateUtil.format((LocalDate) value);
        }
        if (value instanceof java.sql.Date) {
            return DateUtil.format(((java.sql.Date) value).toLocalDate());
        }
        if (value instanceof java.sql.Timestamp) {
            return DateUtil.format(((java.sql.Timestamp) value).toLocalDateTime());
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "Yes" : "No";
        }
        return value.toString();
    }

    public static String heading(String column) {
        StringBuilder out = new StringBuilder();
        for (String part : column.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            String lower = part.toLowerCase();
            if (lower.equals("emi") || lower.equals("id") || lower.equals("foir")) {
                out.append(lower.toUpperCase());
            } else {
                out.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return out.toString();
    }
}
