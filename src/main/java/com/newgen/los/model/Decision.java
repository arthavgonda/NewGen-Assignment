package com.newgen.los.model;

public enum Decision {
    APPROVE,
    REJECT;

    public static Decision from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Decision is required");
        }
        String v = value.trim().toUpperCase();
        if (v.startsWith("A")) {
            return APPROVE;
        }
        if (v.startsWith("R")) {
            return REJECT;
        }
        throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
    }
}
