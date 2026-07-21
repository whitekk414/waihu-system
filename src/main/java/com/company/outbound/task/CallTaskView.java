package com.company.outbound.task;

import java.time.Instant;
import java.util.UUID;

public record CallTaskView(
    UUID id,
    String extension,
    String promptId,
    CallStatus status,
    Instant createdAt,
    Instant updatedAt,
    String lastError,
    int retryCount,
    String transcript,
    String analysisJson
) {
    static CallTaskView from(CallTask task) {
        return new CallTaskView(
            task.getId(), task.getExtension(), task.getPromptId(), task.getStatus(),
            task.getCreatedAt(), task.getUpdatedAt(), task.getLastError(),
            task.getRetryCount(), task.getTranscript(), task.getAnalysisJson()
        );
    }
}
