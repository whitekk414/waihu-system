package com.company.outbound.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
public class CallTask {
    @Id
    private UUID id;
    @Column(nullable = false, length = 20)
    private String extension;
    @Column(nullable = false, length = 64)
    private String promptId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CallStatus status;
    private String ariChannelId;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant updatedAt;
    private Instant ringingAt;
    private Instant answeredAt;
    private Instant endedAt;
    private Instant completedAt;
    private String lastError;
    private int retryCount;
    @Lob
    private String transcript;
    @Lob
    private String analysisJson;
    @Version
    private long version;

    protected CallTask() {
    }

    public CallTask(String extension, String promptId) {
        this.id = UUID.randomUUID();
        this.extension = extension;
        this.promptId = promptId;
        this.status = CallStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getExtension() { return extension; }
    public String getPromptId() { return promptId; }
    public CallStatus getStatus() { return status; }
    public String getAriChannelId() { return ariChannelId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastError() { return lastError; }
    public int getRetryCount() { return retryCount; }
    public String getTranscript() { return transcript; }
    public String getAnalysisJson() { return analysisJson; }

    public void moveTo(CallStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void bindAriChannel(String channelId) {
        this.ariChannelId = channelId;
        this.updatedAt = Instant.now();
    }

    public void saveProcessingResults(String transcript, String analysisJson) {
        this.transcript = transcript;
        this.analysisJson = analysisJson;
        this.updatedAt = Instant.now();
    }
}
