package com.company.outbound.dialog;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
    name = "uk_dialog_turn_task_node_attempt",
    columnNames = {"task_id", "node", "attempt"}))
public class DialogTurn {
    @Id private UUID id;
    @Column(name = "task_id", nullable = false) private UUID taskId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private DialogNode node;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false, length = 80) private String promptId;
    @Column(length = 180) private String recordingName;
    @Lob private String transcript;
    @Enumerated(EnumType.STRING)
    @Column(length = 32) private DialogIntent intent;
    private Double confidence;
    @Lob private String summary;
    @Column(nullable = false) private Instant createdAt;
    private Instant completedAt;

    protected DialogTurn() {}

    public DialogTurn(UUID taskId, DialogNode node, int attempt, String promptId) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.node = node;
        this.attempt = attempt;
        this.promptId = promptId;
        this.createdAt = Instant.now();
    }

    public void complete(String recordingName, String transcript, DialogDecision decision) {
        this.recordingName = recordingName;
        this.transcript = transcript;
        this.intent = decision.intent();
        this.confidence = decision.confidence();
        this.summary = decision.summary();
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public DialogNode getNode() { return node; }
    public int getAttempt() { return attempt; }
    public String getPromptId() { return promptId; }
    public String getRecordingName() { return recordingName; }
    public String getTranscript() { return transcript; }
    public DialogIntent getIntent() { return intent; }
    public Double getConfidence() { return confidence; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
