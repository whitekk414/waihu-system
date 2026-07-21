package com.company.outbound.processing;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.task.CreateCallRequest;
import com.company.outbound.telephony.CallOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:processing;DB_CLOSE_DELAY=-1",
    "outbound.telephony.mode=mock",
    "outbound.processing.mode=mock"
})
class ProcessingOrchestratorTest {
    @Autowired CallTaskService taskService;
    @Autowired CallOrchestrator callOrchestrator;
    @Autowired ProcessingOrchestrator processingOrchestrator;
    @TempDir Path tempDir;

    @Test
    void turnsRecordingIntoCompletedAnalysis() throws Exception {
        UUID taskId = taskService.create(new CreateCallRequest("1001", "payment-reminder")).id();
        callOrchestrator.start(taskId);
        Path recording = tempDir.resolve("call.wav");
        Files.write(recording, new byte[128]);

        processingOrchestrator.process(taskId, recording);

        var result = taskService.get(taskId);
        assertThat(result.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(result.transcript()).contains("测试客户");
        assertThat(result.analysisJson()).contains("needsHumanReview");
    }
}
