package com.newgen.los.service;

import com.newgen.los.exception.ValidationException;

final class UserCodes {

    static final String SYSTEM = "SYSTEM";

    private UserCodes() {
    }

    static String clean(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            throw new ValidationException("User code is required");
        }
        String code = userCode.trim().toUpperCase();
        if (code.length() > 20 || !code.matches("[A-Z0-9_]+")) {
            throw new ValidationException("User code must be up to 20 letters, digits or underscore");
        }
        return code;
    }
}
