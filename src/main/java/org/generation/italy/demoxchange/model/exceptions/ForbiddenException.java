package org.generation.italy.demoxchange.model.exceptions;

public class ForbiddenException extends RuntimeException {
    private final String errorCode;

    public ForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

