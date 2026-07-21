package com.company.outbound.events;

import com.company.outbound.task.CallStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:event-api;DB_CLOSE_DELAY=-1")
class TaskEventServiceTest {
    @Autowired TaskEventService service;

    @Test
    void persistsPublishedEvent() {
        UUID taskId = UUID.randomUUID();
        service.publish(taskId, CallStatus.PENDING, "任务已创建");

        assertThat(service.history(taskId))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.taskId()).isEqualTo(taskId);
                assertThat(event.status()).isEqualTo(CallStatus.PENDING);
            });
    }
}
