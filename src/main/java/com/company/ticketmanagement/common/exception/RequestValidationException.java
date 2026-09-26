package com.company.ticketmanagement.common.exception;

public class RequestValidationException extends AppException {

    public RequestValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
