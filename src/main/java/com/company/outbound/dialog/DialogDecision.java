package com.company.outbound.dialog;

public record DialogDecision(
    DialogIntent intent,
    double confidence,
    DialogNode nextNode,
    boolean needHuman,
    String summary
) {
    public DialogDecision {
        if (intent == null) throw new IllegalArgumentException("intent is required");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }
}
