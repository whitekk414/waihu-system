package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.task.CreateCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mock-lifecycle;DB_CLOSE_DELAY=-1",
    "outbound.telephony.mode=mock"
})
class MockTelephonyLifecycleTest {
    @Autowired CallTaskService taskService;
    @Autowired CallOrchestrator orchestrator;

    @Test
    void simulatesCallThroughRecordingReady() {
        UUID taskId = taskService.create(new CreateCallRequest("1001", "payment-reminder")).id();

        orchestrator.start(taskId);

        assertThat(taskService.get(taskId).status()).isEqualTo(CallStatus.RECORDING_READY);
    }
}
