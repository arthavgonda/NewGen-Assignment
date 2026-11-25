package com.newgen.los.service;

import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.model.ApplicationStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.newgen.los.model.ApplicationStatus.APPROVED;
import static com.newgen.los.model.ApplicationStatus.CORE_BANKING_PUSH;
import static com.newgen.los.model.ApplicationStatus.CREDIT_APPROVAL;
import static com.newgen.los.model.ApplicationStatus.DISBURSED;
import static com.newgen.los.model.ApplicationStatus.DRAFT;
import static com.newgen.los.model.ApplicationStatus.EXIT;
import static com.newgen.los.model.ApplicationStatus.REJECTED;
import static com.newgen.los.model.ApplicationStatus.UNDER_REVIEW;

public final class WorkflowRules {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(UNDER_REVIEW, REJECTED));
        ALLOWED.put(UNDER_REVIEW, EnumSet.of(CREDIT_APPROVAL, REJECTED));
        ALLOWED.put(CREDIT_APPROVAL, EnumSet.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED, EnumSet.of(DISBURSED));
        ALLOWED.put(DISBURSED, EnumSet.of(CORE_BANKING_PUSH));
        ALLOWED.put(CORE_BANKING_PUSH, EnumSet.of(EXIT));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(EXIT, EnumSet.noneOf(ApplicationStatus.class));
    }

    private WorkflowRules() {
    }

    public static boolean canMove(ApplicationStatus from, ApplicationStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void ensure(ApplicationStatus from, ApplicationStatus to) {
        if (!canMove(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    public static Set<ApplicationStatus> nextStatuses(ApplicationStatus from) {
        Set<ApplicationStatus> next = ALLOWED.get(from);
        return next == null || next.isEmpty() ? EnumSet.noneOf(ApplicationStatus.class) : EnumSet.copyOf(next);
    }

    public static boolean isTerminal(ApplicationStatus status) {
        return nextStatuses(status).isEmpty();
    }
}
