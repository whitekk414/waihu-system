package com.company.outbound.processing;

import com.company.outbound.dialog.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "outbound.processing.decision", havingValue = "gpt")
class GptDecisionAdapter implements DecisionPort {
    private final String endpoint;
    private final String llmEnv;
    private final String tenantId;
    private final String model;
    private final Duration requestTimeout;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final DialogStateMachine stateMachine = new DialogStateMachine();

    @Autowired
    GptDecisionAdapter(
        @Value("${outbound.processing.gpt.base-url}") String baseUrl,
        @Value("${outbound.processing.gpt.llm-env:UAT}") String llmEnv,
        @Value("${outbound.processing.gpt.tenant-id:10001}") String tenantId,
        @Value("${outbound.processing.gpt.model}") String model,
        @Value("${outbound.processing.gpt.timeout-seconds:15}") int timeoutSeconds) {
        this.endpoint = baseUrl;
        this.llmEnv = llmEnv;
        this.tenantId = tenantId;
        this.model = model;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    GptDecisionAdapter(String baseUrl, String ignoredApiKey, String model) {
        this(baseUrl.replaceAll("/+$", "") + "/chat/completions", "UAT", "10001", model, 15);
    }

    @Override
    public DialogDecision decide(DialogNode node, String transcript) {
        try {
            String prompt = "Node=" + node + "; transcript=" + transcript
                + "; return JSON with intent,confidence,nextNode,needHuman,summary only.";
            String requestJson = mapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-LLM-Env", llmEnv)
                .header("X-Request-Id", "waihu-" + java.util.UUID.randomUUID())
                .header("tenant_id", tenantId)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Decision request failed: HTTP " + response.statusCode());
            }
            JsonNode envelope = mapper.readTree(response.body());
            String content = envelope.path("choices").path(0).path("message").path("content").asText();
            JsonNode json = mapper.readTree(content);
            DialogIntent intent = DialogIntent.valueOf(json.path("intent").asText());
            DialogNode nextNode = DialogNode.valueOf(json.path("nextNode").asText());
            DialogDecision decision = new DialogDecision(intent, json.path("confidence").asDouble(-1),
                nextNode, json.path("needHuman").asBoolean(), json.path("summary").asText());
            DialogTransition allowed = stateMachine.decide(node, intent, 0);
            if (allowed.nextNode() != nextNode) {
                throw new IllegalArgumentException("nextNode is not allowed for the selected intent");
            }
            return decision;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Decision request interrupted", error);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot obtain a valid decision", error);
        }
    }
}
