package com.company.ticketmanagement.ingestion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashEmbeddingClientTest {

    @Test
    void embed_isDeterministicAndMatchesPgvectorWidth() {
        HashEmbeddingClient client = new HashEmbeddingClient(32);

        float[] first = client.embed("Office VPN drops hourly");
        float[] second = client.embed("Office VPN drops hourly");

        assertThat(client.dimensions()).isEqualTo(32);
        assertThat(first).hasSize(32).containsExactly(second);
        double norm = 0d;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(norm).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.0001d));
    }
}
