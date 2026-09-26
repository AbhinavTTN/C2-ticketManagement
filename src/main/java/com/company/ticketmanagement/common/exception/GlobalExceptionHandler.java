package com.company.ticketmanagement.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.company.ticketmanagement.ask.application.LanguageModelNotConfiguredException;
import com.company.ticketmanagement.ticket.domain.TicketNotFoundException;
import com.company.ticketmanagement.ticket.domain.TicketStateConflictException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TicketNotFoundException.class)
    ProblemDetail notFound(TicketNotFoundException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        body.setTitle("Ticket not found");
        body.setProperty("code", ex.getCode().name());
        return body;
    }

    @ExceptionHandler(TicketStateConflictException.class)
    ProblemDetail conflict(TicketStateConflictException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Invalid ticket status transition");
        body.setProperty("code", ex.getCode().name());
        return body;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed. Correct the highlighted fields and try again.");
        body.setTitle("Validation failed");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        body.setProperty("fields", fields);
        return body;
    }

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail requestValidation(RequestValidationException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        body.setTitle("Validation failed");
        body.setProperty("code", ex.getCode().name());
        return body;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body is invalid. Check JSON syntax and allowed enum values.");
        body.setTitle("Malformed request");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        return body;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Parameter '%s' has an invalid value.".formatted(ex.getName()));
        body.setTitle("Invalid parameter");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        return body;
    }

    @ExceptionHandler(LanguageModelNotConfiguredException.class)
    ProblemDetail languageModel(LanguageModelNotConfiguredException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        body.setTitle("Language model is not configured");
        body.setProperty("code", ex.getCode().name());
        return body;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unhandled(Exception ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again.");
        body.setTitle("Internal server error");
        body.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
        return body;
    }
}
