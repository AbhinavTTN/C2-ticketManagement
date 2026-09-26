package com.company.ticketmanagement.ask.application;

import com.company.ticketmanagement.common.exception.AppException;
import com.company.ticketmanagement.common.exception.ErrorCode;

public class LanguageModelNotConfiguredException extends AppException {

    public LanguageModelNotConfiguredException() {
        super(ErrorCode.LANGUAGE_MODEL_NOT_CONFIGURED, "Language model is not configured.");
    }
}
