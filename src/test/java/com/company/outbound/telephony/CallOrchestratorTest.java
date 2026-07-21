package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.task.CreateCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:call-orchestrator;DB_CLOSE_DELAY=-1",
    "outbound.telephony.mode=test"
})
class CallOrchestratorTest {
    @Autowired CallTaskService taskService;
    @Autowired CallOrchestrator orchestrator;
    @Autowired FakeTelephonyPort fake;

    @Test
    void startsPendingTaskExactlyOnce() {
        UUID taskId = taskService.create(new CreateCallRequest("1001", "payment-reminder")).id();

        orchestrator.start(taskId);

        assertThat(fake.commands()).containsExactly(new CallCommand(taskId, "1001", "payment-reminder"));
        assertThat(taskService.get(taskId).status()).isEqualTo(CallStatus.ORIGINATING);
    }

    @Test
    void appliesTelephonyEventOnce() {
        UUID taskId = taskService.create(new CreateCallRequest("1002", "payment-reminder")).id();
        orchestrator.start(taskId);
        TelephonyEvent event = new TelephonyEvent(
            UUID.randomUUID(), taskId, CallStatus.RINGING, "分机振铃", null);

        fake.emit(event);
        fake.emit(event);

        assertThat(taskService.get(taskId).status()).isEqualTo(CallStatus.RINGING);
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary
        FakeTelephonyPort fakeTelephonyPort() {
            return new FakeTelephonyPort();
        }
    }

    static class FakeTelephonyPort implements TelephonyPort {
        private final List<CallCommand> commands = new ArrayList<>();
        private Consumer<TelephonyEvent> listener = ignored -> { };

        @Override
        public String originate(CallCommand command) {
            commands.add(command);
            return "test-channel";
        }

        @Override
        public void setEventListener(Consumer<TelephonyEvent> listener) {
            this.listener = listener;
        }

        void emit(TelephonyEvent event) {
            listener.accept(event);
        }

        List<CallCommand> commands() {
            return List.copyOf(commands);
        }
    }
}
