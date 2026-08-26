package com.company.outbound.processing;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "outbound.dialog.enabled", havingValue = "false", matchIfMissing = true)
public class ProcessingOrchestrator {
    private final CallTaskService taskService;
    private final RecordingService recordingService;
    private final TranscriptionPort transcriptionPort;
    private final AnalysisPort analysisPort;
    private final ObjectMapper objectMapper;

    public ProcessingOrchestrator(CallTaskService taskService, RecordingService recordingService,
                                  TranscriptionPort transcriptionPort, AnalysisPort analysisPort,
                                  ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.recordingService = recordingService;
        this.transcriptionPort = transcriptionPort;
        this.analysisPort = analysisPort;
        this.objectMapper = objectMapper;
    }

    public void process(UUID taskId, Path recording) {
        recordingService.validateAndHash(recording);
        taskService.transition(taskId, CallStatus.TRANSCRIBING, "正在转写录音");
        String transcript = transcriptionPort.transcribe(recording);
        taskService.transition(taskId, CallStatus.ANALYZING, "正在进行模型分析");
        AnalysisResult analysis = analysisPort.analyze(transcript);
        taskService.saveProcessingResults(taskId, transcript, toJson(analysis));
        taskService.transition(taskId, CallStatus.COMPLETED, "任务处理完成");
    }

    private String toJson(AnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize analysis result", error);
        }
    }
}
