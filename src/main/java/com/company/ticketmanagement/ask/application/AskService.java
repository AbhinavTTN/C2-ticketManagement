package com.company.ticketmanagement.ask.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.ticketmanagement.ask.dto.AskRequest;
import com.company.ticketmanagement.ask.dto.AskResponse;
import com.company.ticketmanagement.ask.dto.Citation;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;

@Service
@Transactional(readOnly = true)
public class AskService {

    private final ChunkRetriever chunkRetriever;
    private final TicketRepository ticketRepository;
    private final LlmClient llmClient;
    private final AskProperties properties;

    public AskService(
            ChunkRetriever chunkRetriever,
            TicketRepository ticketRepository,
            LlmClient llmClient,
            AskProperties properties) {
        this.chunkRetriever = chunkRetriever;
        this.ticketRepository = ticketRepository;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public AskResponse ask(AskRequest request) {
        List<RetrievedChunk> nearest = chunkRetriever.search(request.question(), properties.topK());
        List<RetrievedChunk> relevant = nearest.stream()
                .filter(hit -> hit.similarity() >= properties.similarityThreshold())
                .toList();
        Map<Long, Ticket> tickets = loadTickets(relevant);
        List<RetrievedChunk> usable = relevant.stream()
                .filter(hit -> tickets.containsKey(hit.ticketId()))
                .toList();
        if (usable.isEmpty()) {
            return new AskResponse(request.question(), false, AskPrompt.NO_MATCH, List.of());
        }
        String answer = llmClient.complete(AskPrompt.build(request.question(), usable));
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("Language model returned an empty answer.");
        }
        List<Citation> citations = new ArrayList<>();
        for (Map.Entry<Long, Ticket> entry : tickets.entrySet()) {
            Ticket ticket = entry.getValue();
            citations.add(new Citation(entry.getKey(), ticket.getTitle(), ticket.getStatus()));
        }
        return new AskResponse(request.question(), true, answer, citations);
    }

    private Map<Long, Ticket> loadTickets(List<RetrievedChunk> chunks) {
        Map<Long, Ticket> tickets = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (tickets.containsKey(chunk.ticketId())) {
                continue;
            }
            ticketRepository.findById(chunk.ticketId()).ifPresent(ticket -> tickets.put(chunk.ticketId(), ticket));
        }
        return tickets;
    }
}
