package com.company.outbound.processing;

import com.company.outbound.dialog.DialogIntent;
import com.company.outbound.dialog.DialogNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GptDecisionAdapterTest {
    @Test
    void sendsModelAuditUatHeadersAndModel() throws Exception {
        AtomicReference<String> env = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        String content = "{\\\"intent\\\":\\\"SELF_CONFIRMED\\\",\\\"confidence\\\":0.96,"
            + "\\\"nextNode\\\":\\\"ASK_PAYMENT_PLAN\\\",\\\"needHuman\\\":false,\\\"summary\\\":\\\"本人\\\"}";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/model-audit-platform/api/v1/chat/completions", exchange -> {
            env.set(exchange.getRequestHeaders().getFirst("X-LLM-Env"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            tenant.set(exchange.getRequestHeaders().getFirst("tenant_id"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = ("{\"choices\":[{\"message\":{\"content\":\"" + content
                + "\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new GptDecisionAdapter(
                "http://localhost:" + server.getAddress().getPort()
                    + "/model-audit-platform/api/v1/chat/completions",
                "UAT", "10001", "openai.gpt-5.4", 15);

            adapter.decide(DialogNode.ASK_IDENTITY, "是本人");

            assertThat(env.get()).isEqualTo("UAT");
            assertThat(requestId.get()).startsWith("waihu-");
            assertThat(tenant.get()).isEqualTo("10001");
            assertThat(body.get()).contains("\"model\":\"openai.gpt-5.4\"");
        } finally { server.stop(0); }
    }

    @Test
    void returnsValidatedStructuredDecision() throws Exception {
        String content = "{\\\"intent\\\":\\\"SELF_CONFIRMED\\\",\\\"confidence\\\":0.96,"
            + "\\\"nextNode\\\":\\\"ASK_PAYMENT_PLAN\\\",\\\"needHuman\\\":false,\\\"summary\\\":\\\"本人\\\"}";
        HttpServer server = server("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}");
        try {
            var adapter = new GptDecisionAdapter(url(server), "key", "gpt-test");
            var decision = adapter.decide(DialogNode.ASK_IDENTITY, "是我本人");
            assertThat(decision.intent()).isEqualTo(DialogIntent.SELF_CONFIRMED);
            assertThat(decision.nextNode()).isEqualTo(DialogNode.ASK_PAYMENT_PLAN);
        } finally { server.stop(0); }
    }

    @Test
    void rejectsNodeNotAllowedByStateMachine() throws Exception {
        String content = "{\\\"intent\\\":\\\"SELF_CONFIRMED\\\",\\\"confidence\\\":0.9,"
            + "\\\"nextNode\\\":\\\"COMPLETED\\\",\\\"needHuman\\\":false,\\\"summary\\\":\\\"wrong\\\"}";
        HttpServer server = server("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}");
        try {
            var adapter = new GptDecisionAdapter(url(server), "key", "gpt-test");
            assertThatThrownBy(() -> adapter.decide(DialogNode.ASK_IDENTITY, "是本人"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextNode");
        } finally { server.stop(0); }
    }

    private static HttpServer server(String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
