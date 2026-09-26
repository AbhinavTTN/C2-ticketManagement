package com.company.ticketmanagement.common.exception;

public abstract class AppException extends RuntimeException {

    private final ErrorCode code;

    protected AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
