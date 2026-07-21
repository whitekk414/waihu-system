package com.company.outbound.events;

import com.company.outbound.task.CallStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskEventView(
    UUID id,
    UUID taskId,
    CallStatus status,
    String message,
    Instant occurredAt
) {
    static TaskEventView from(TaskEvent event) {
        return new TaskEventView(event.getId(), event.getTaskId(), event.getStatus(),
            event.getMessage(), event.getOccurredAt());
    }
}
