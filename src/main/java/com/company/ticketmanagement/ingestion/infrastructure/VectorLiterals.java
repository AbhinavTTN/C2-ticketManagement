package com.company.ticketmanagement.ingestion.infrastructure;

public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(embedding[i]);
        }
        literal.append(']');
        return literal.toString();
    }
}
