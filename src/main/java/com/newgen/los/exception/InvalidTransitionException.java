package com.newgen.los.exception;

import com.newgen.los.model.ApplicationStatus;

public class InvalidTransitionException extends LoanException {

    public InvalidTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Status change " + from + " -> " + to + " is not allowed");
    }

    public InvalidTransitionException(String message) {
        super(message);
    }
}
