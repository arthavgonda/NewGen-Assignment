package com.newgen.los.model;

public enum Product {
    PERSONAL,
    EDUCATION;

    public static Product from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product is required");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown product: " + value + " (use PERSONAL or EDUCATION)");
        }
    }
}
