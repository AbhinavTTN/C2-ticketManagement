package com.company.ticketmanagement.ask.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.ticketmanagement.ask.application.AskService;
import com.company.ticketmanagement.ask.dto.AskRequest;
import com.company.ticketmanagement.ask.dto.AskResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request);
    }
}
