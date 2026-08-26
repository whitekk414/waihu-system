package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.task.CreateCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mock-lifecycle;DB_CLOSE_DELAY=-1",
    "outbound.telephony.mode=mock"
})
class MockTelephonyLifecycleTest {
    @Autowired CallTaskService taskService;
    @Autowired CallOrchestrator orchestrator;

    @Test
    void simulatesCallAnswer() {
        UUID taskId = taskService.create(new CreateCallRequest("1001", "payment-reminder")).id();

        orchestrator.start(taskId);

        assertThat(taskService.get(taskId).status()).isEqualTo(CallStatus.ANSWERED);
    }

    @Test
    void emitsDeterministicMediaLifecycle() {
        MockTelephonyAdapter adapter = new MockTelephonyAdapter();
        List<TelephonyEvent> events = new ArrayList<>();
        adapter.setEventListener(events::add);
        UUID taskId = UUID.randomUUID();

        String channel = adapter.originate(new CallCommand(taskId, "1001", "identity"));
        String play = adapter.play(taskId, channel, "identity");
        String recording = adapter.record(taskId, channel, "answer-q1", 20, 3);
        adapter.hangup(taskId, channel);

        assertThat(play).startsWith("mock-play-");
        assertThat(recording).isEqualTo("answer-q1");
        assertThat(events).extracting(TelephonyEvent::type).containsExactly(
            TelephonyEventType.CHANNEL_ANSWERED,
            TelephonyEventType.PLAYBACK_FINISHED,
            TelephonyEventType.RECORDING_FINISHED,
            TelephonyEventType.CHANNEL_ENDED);
    }
}
