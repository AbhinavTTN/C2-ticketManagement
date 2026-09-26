package com.company.ticketmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.company.ticketmanagement.ask.application.AskProperties;
import com.company.ticketmanagement.ask.infrastructure.LlmProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AskProperties.class, LlmProperties.class})
public class TicketManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketManagementApplication.class, args);
    }
}
