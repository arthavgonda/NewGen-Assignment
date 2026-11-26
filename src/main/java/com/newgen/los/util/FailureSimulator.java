package com.newgen.los.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// only used to demo rollback: arm a point and the next transaction through it blows up
public final class FailureSimulator {

    public static final String SUBMISSION = "SUBMISSION";
    public static final String DECISION = "DECISION";
    public static final String DISBURSEMENT = "DISBURSEMENT";

    private static final Set<String> ARMED = ConcurrentHashMap.newKeySet();

    private FailureSimulator() {
    }

    public static void arm(String point) {
        ARMED.add(point);
    }

    public static boolean isArmed(String point) {
        return ARMED.contains(point);
    }

    public static void disarmAll() {
        ARMED.clear();
    }

    public static void check(String point) {
        if (ARMED.remove(point)) {
            throw new SimulatedFailureException("Simulated failure at " + point + " step, transaction rolled back");
        }
    }

    public static class SimulatedFailureException extends RuntimeException {
        public SimulatedFailureException(String message) {
            super(message);
        }
    }
}
