package com.newgen.los.dao;

import com.newgen.los.exception.DataAccessException;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuplicateKeyException extends DataAccessException {

    private static final Pattern KEY = Pattern.compile("for key '(?:[\\w]+\\.)?([\\w]+)'");

    private final String constraint;

    public DuplicateKeyException(SQLException cause) {
        super(cause.getMessage(), cause);
        Matcher m = KEY.matcher(String.valueOf(cause.getMessage()));
        this.constraint = m.find() ? m.group(1) : "";
    }

    public String getConstraint() {
        return constraint;
    }

    public boolean isFor(String constraintName) {
        return constraint.equalsIgnoreCase(constraintName);
    }
}
