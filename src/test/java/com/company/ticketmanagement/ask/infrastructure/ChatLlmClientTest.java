package com.company.ticketmanagement.ask.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.company.ticketmanagement.ask.application.LanguageModelNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatLlmClientTest {

    @Test
    void complete_postsThePromptAndReturnsTheMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://llm.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {"model":"test-model","messages":[{"role":"user","content":"prompt text"}]}
                        """))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"content":"Reset email never arrives"}}]}
                        """,
                        MediaType.APPLICATION_JSON));
        ChatLlmClient client = new ChatLlmClient(
                new LlmProperties("http://llm.test/v1/", "test-model", "test-key"),
                builder,
                new ObjectMapper());

        assertThat(client.complete("prompt text")).isEqualTo("Reset email never arrives");
        server.verify();
    }

    @Test
    void complete_whenModelIsMissing_doesNotCallTheEndpoint() {
        ChatLlmClient client = new ChatLlmClient(
                new LlmProperties("http://llm.test/v1", "  ", null),
                RestClient.builder(),
                new ObjectMapper());

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(LanguageModelNotConfiguredException.class)
                .hasMessage("Language model is not configured.");
    }
}
