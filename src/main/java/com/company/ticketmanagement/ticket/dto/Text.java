package com.company.ticketmanagement.ticket.dto;

final class Text {

    private Text() {
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
