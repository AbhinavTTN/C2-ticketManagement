package com.company.ticketmanagement.ask.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TicketIdMentions {

    private static final Pattern NAMED_TICKET = Pattern.compile("(?i)\\bticket\\s*(?:id\\s*)?#?\\s*(\\d+)");

    private TicketIdMentions() {
    }

    static List<Long> find(String question) {
        Matcher matcher = NAMED_TICKET.matcher(question);
        Set<Long> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return List.copyOf(ids);
    }
}
