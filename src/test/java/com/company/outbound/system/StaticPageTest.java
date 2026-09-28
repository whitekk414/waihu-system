package com.company.outbound.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:static-page;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StaticPageTest {
    @Autowired MockMvc mvc;

    @Test
    void servesOperationsWorkbench() throws Exception {
        mvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("CallFlow Studio")))
            .andExpect(content().string(containsString("operations-shell")))
            .andExpect(content().string(containsString("metricGrid")))
            .andExpect(content().string(containsString("callStage")))
            .andExpect(content().string(containsString("conversationPanel")))
            .andExpect(content().string(containsString("dialogTimeline")))
            .andExpect(content().string(containsString("我已获得授权，确认拨打该号码")));
    }

    @Test
    void clientSafelyHandlesEmptyAndNonJsonResponses() throws Exception {
        mvc.perform(get("/app.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("if (!text.trim() && response.ok) return null")))
            .andExpect(content().string(containsString("content-type")));
    }

    @Test
    void servesWorkbenchDesignSystem() throws Exception {
        mvc.perform(get("/styles.css"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("--accent-mint")))
            .andExpect(content().string(containsString(".workspace-grid")))
            .andExpect(content().string(containsString(".call-wave")))
            .andExpect(content().string(containsString("@keyframes")))
            .andExpect(content().string(containsString("prefers-reduced-motion")));
    }

    @Test
    void clientRendersWorkbenchFromRealApis() throws Exception {
        mvc.perform(get("/app.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("deriveMetrics")))
            .andExpect(content().string(containsString("statusTone")))
            .andExpect(content().string(containsString("renderMetrics")))
            .andExpect(content().string(containsString("renderCallStage")))
            .andExpect(content().string(containsString("renderConversation")))
            .andExpect(content().string(containsString("renderTimeline")))
            .andExpect(content().string(containsString("/api/tasks/${id}/turns")))
            .andExpect(content().string(containsString("/api/events/tasks/${id}")));
    }
}
