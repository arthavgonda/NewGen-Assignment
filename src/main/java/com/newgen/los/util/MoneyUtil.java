package com.newgen.los.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    private MoneyUtil() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    // indian grouping, 12345678.5 -> 1,23,45,678.50
    public static String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal scaled = scale(value);
        String sign = scaled.signum() < 0 ? "-" : "";
        String plain = scaled.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String fraction = plain.substring(dot + 1);

        if (whole.length() <= 3) {
            return sign + whole + "." + fraction;
        }
        String lastThree = whole.substring(whole.length() - 3);
        String rest = whole.substring(0, whole.length() - 3);
        StringBuilder grouped = new StringBuilder();
        int end = rest.length();
        while (end > 2) {
            grouped.insert(0, "," + rest.substring(end - 2, end));
            end -= 2;
        }
        grouped.insert(0, rest.substring(0, end));
        return sign + grouped + "," + lastThree + "." + fraction;
    }

    public static String inr(BigDecimal value) {
        return value == null ? "" : "INR " + format(value);
    }

    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim().replace(",", "").replace("INR", "").replace("₹", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid amount: " + text);
        }
    }
}
