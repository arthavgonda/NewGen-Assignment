package com.newgen.los.model;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    DRAFT("Draft"),
    UNDER_REVIEW("Under Review"),
    CREDIT_APPROVAL("Credit Approval"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    DISBURSED("Disbursed"),
    CORE_BANKING_PUSH("Core Banking Push"),
    EXIT("Exit");

    private static final Set<ApplicationStatus> ACTIVE = EnumSet.of(DRAFT, UNDER_REVIEW, CREDIT_APPROVAL, APPROVED);

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public static Set<ApplicationStatus> activeStatuses() {
        return EnumSet.copyOf(ACTIVE);
    }

    public static ApplicationStatus from(String value) {
        try {
            return valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }
}
