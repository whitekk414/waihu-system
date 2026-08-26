package com.company.outbound.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbound.dialog.enabled=true",
    "outbound.processing.mode=real",
    "outbound.processing.transcription=sensevoice",
    "outbound.processing.decision=rule"
})
class DialogModeContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void dialogModeDoesNotLoadLegacyProcessingOrchestrator() {
        assertThat(context.getBeansOfType(ProcessingOrchestrator.class)).isEmpty();
    }
}
