package com.company.outbound.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "outbound.dialog.enabled=true",
    "outbound.telephony.mode=mock",
    "outbound.processing.mode=real",
    "outbound.processing.transcription=sensevoice",
    "outbound.processing.decision=gpt"
})
class GptDialogModeContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void gptDialogModeExposesFallbackAsDecisionPort() {
        assertThat(context.getBean(DecisionPort.class))
            .isInstanceOf(FallbackDecisionAdapter.class);
    }
}
