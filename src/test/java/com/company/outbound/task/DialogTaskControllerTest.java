package com.company.outbound.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:dialog-api;DB_CLOSE_DELAY=-1",
    "outbound.dialog.enabled=true",
    "outbound.telephony.mode=mock",
    "outbound.processing.mode=mock",
    "outbound.processing.decision=rule"
})
@AutoConfigureMockMvc
class DialogTaskControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void requiresExplicitConfirmation() throws Exception {
        String id = create("15100000000");
        mvc.perform(post("/api/tasks/{id}/start-dialog", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":false}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonMobileNumberBeforeOriginating() throws Exception {
        String id = create("1001");
        mvc.perform(post("/api/tasks/{id}/start-dialog", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsConfirmedSingleMobileDialog() throws Exception {
        String id = create("15100000000");
        mvc.perform(post("/api/tasks/{id}/start-dialog", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true}"))
            .andExpect(status().isAccepted());
        mvc.perform(get("/api/tasks/{id}/turns", id))
            .andExpect(status().isOk());
    }

    private String create(String number) throws Exception {
        String body = mvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extension\":\"" + number + "\",\"promptId\":\"identity-question\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("id").asText();
    }
}
