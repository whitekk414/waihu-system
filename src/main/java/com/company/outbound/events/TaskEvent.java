package com.company.outbound.events;

import com.company.outbound.task.CallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class TaskEvent {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(nullable = false)
    private UUID taskId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CallStatus status;
    @Column(nullable = false, length = 256)
    private String message;
    @Column(nullable = false)
    private Instant occurredAt;

    protected TaskEvent() {
    }

    public TaskEvent(UUID taskId, CallStatus status, String message) {
        this.taskId = taskId;
        this.status = status;
        this.message = message;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public CallStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}
