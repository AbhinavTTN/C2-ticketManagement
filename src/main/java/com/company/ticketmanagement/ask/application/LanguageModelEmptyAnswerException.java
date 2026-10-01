package com.company.ticketmanagement.ask.application;

import com.company.ticketmanagement.common.exception.AppException;
import com.company.ticketmanagement.common.exception.ErrorCode;

public class LanguageModelEmptyAnswerException extends AppException {

    public LanguageModelEmptyAnswerException(String message) {
        super(ErrorCode.LANGUAGE_MODEL_EMPTY_ANSWER, message);
    }
}
