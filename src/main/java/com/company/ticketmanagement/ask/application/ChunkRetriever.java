package com.company.ticketmanagement.ask.application;

import java.util.List;

public interface ChunkRetriever {

    List<RetrievedChunk> search(String question, int topK);
}
