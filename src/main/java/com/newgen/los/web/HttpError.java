package com.newgen.los.web;

class HttpError extends RuntimeException {

    private final int status;

    HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
