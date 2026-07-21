package com.company.outbound.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:task-api;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallTaskControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsAndReadsTask() throws Exception {
        String body = mvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extension\":\"1001\",\"promptId\":\"payment-reminder\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(body).get("id").asText();
        mvc.perform(get("/api/tasks/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.extension").value("1001"));
    }

    @Test
    void rejectsInvalidExtension() throws Exception {
        mvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extension\":\"not-a-number\",\"promptId\":\"payment-reminder\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void startsCreatedTask() throws Exception {
        String body = mvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extension\":\"1003\",\"promptId\":\"payment-reminder\"}"))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mvc.perform(post("/api/tasks/{id}/start", id))
            .andExpect(status().isAccepted());

        mvc.perform(get("/api/tasks/{id}", id))
            .andExpect(jsonPath("$.status").value("RECORDING_READY"));
    }
}
