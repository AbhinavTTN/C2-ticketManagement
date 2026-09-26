package com.company.ticketmanagement.ask.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.ticketmanagement.ask.dto.AskRequest;
import com.company.ticketmanagement.ticket.domain.Ticket;
import com.company.ticketmanagement.ticket.domain.TicketPriority;
import com.company.ticketmanagement.ticket.domain.TicketStatus;
import com.company.ticketmanagement.ticket.infrastructure.TicketRepository;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    @Mock
    ChunkRetriever chunkRetriever;

    @Mock
    TicketRepository ticketRepository;

    @Mock
    LlmClient llmClient;

    AskService askService;

    @BeforeEach
    void setUp() {
        askService = new AskService(chunkRetriever, ticketRepository, llmClient, new AskProperties(2, 0.25));
    }

    @Test
    void ask_whenBestScoreIsBelowThreshold_doesNotCallTheModel() {
        given(chunkRetriever.search("lunar rover", 2)).willReturn(List.of(
                new RetrievedChunk(101L, "Reset email never arrives", 0.249)));

        var response = askService.ask(new AskRequest("lunar rover"));

        assertThat(response.found()).isFalse();
        assertThat(response.answer()).isEqualTo("No relevant tickets were found.");
        assertThat(response.citations()).isEmpty();
        verify(llmClient, never()).complete(any());
        verify(ticketRepository, never()).findById(any());
    }

    @Test
    void ask_whenScoreMeetsThreshold_promptsWithThoseChunksOnlyAndCitesThem() {
        Ticket password = new Ticket("Cannot reset password", "Reset email never arrives", TicketPriority.HIGH, "Sam", "email");
        Ticket vpn = new Ticket("VPN timeout", "Office VPN drops hourly", TicketPriority.MEDIUM, null, "network");
        vpn.transitionTo(TicketStatus.IN_PROGRESS);
        given(chunkRetriever.search("password or vpn", 2)).willReturn(List.of(
                new RetrievedChunk(101L, "Reset email never arrives", 0.25),
                new RetrievedChunk(104L, "Floor 3 printer jammed", 0.1),
                new RetrievedChunk(103L, "Office VPN drops hourly", 0.8)));
        given(ticketRepository.findById(101L)).willReturn(Optional.of(password));
        given(ticketRepository.findById(103L)).willReturn(Optional.of(vpn));
        given(llmClient.complete(any())).willReturn("Reset email never arrives. See ticket 101. Office VPN drops hourly.");

        var response = askService.ask(new AskRequest("password or vpn"));

        assertThat(response.found()).isTrue();
        assertThat(response.citations()).extracting(citation -> citation.ticketId()).containsExactly(101L, 103L);
        assertThat(response.citations().get(0).title()).isEqualTo("Cannot reset password");
        assertThat(response.citations().get(0).status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.citations().get(1).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(prompt.capture());
        assertThat(prompt.getValue()).contains("password or vpn");
        assertThat(prompt.getValue()).contains("[ticket 101]").contains("Reset email never arrives");
        assertThat(prompt.getValue()).contains("[ticket 103]").contains("Office VPN drops hourly");
        assertThat(prompt.getValue()).doesNotContain("printer");
        verify(ticketRepository, never()).findById(eq(104L));
    }

    @Test
    void ask_whenRetrievedTicketIsGone_returnsNoMatch() {
        given(chunkRetriever.search("ticket 999999", 2))
                .willReturn(List.of(new RetrievedChunk(999999L, "missing", 0.9)));
        given(ticketRepository.findById(999999L)).willReturn(Optional.empty());

        var response = askService.ask(new AskRequest("ticket 999999"));

        assertThat(response.found()).isFalse();
        assertThat(response.citations()).isEmpty();
        verify(llmClient, never()).complete(any());
    }

    @Test
    void ask_whenSameTicketHasSeveralChunks_citesItOnce() {
        Ticket ticket = new Ticket("Email bounce", "Outbound mail is bouncing", TicketPriority.LOW, null, "billing");
        given(chunkRetriever.search("billing email", 2)).willReturn(List.of(
                new RetrievedChunk(105L, "Outbound mail is bouncing", 0.7),
                new RetrievedChunk(105L, "SPF record added", 0.6)));
        given(ticketRepository.findById(105L)).willReturn(Optional.of(ticket));
        given(llmClient.complete(any())).willReturn("SPF record added.");

        var response = askService.ask(new AskRequest("billing email"));

        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().ticketId()).isEqualTo(105L);
    }
}
