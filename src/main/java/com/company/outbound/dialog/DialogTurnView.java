package com.company.outbound.dialog;

import java.time.Instant;
import java.util.UUID;

public record DialogTurnView(
    UUID id,
    DialogNode node,
    int attempt,
    String promptId,
    String recordingName,
    String transcript,
    DialogIntent intent,
    Double confidence,
    String summary,
    Instant createdAt,
    Instant completedAt
) {
    public static DialogTurnView from(DialogTurn turn) {
        return new DialogTurnView(turn.getId(), turn.getNode(), turn.getAttempt(), turn.getPromptId(),
            turn.getRecordingName(), turn.getTranscript(), turn.getIntent(), turn.getConfidence(),
            turn.getSummary(), turn.getCreatedAt(), turn.getCompletedAt());
    }
}
