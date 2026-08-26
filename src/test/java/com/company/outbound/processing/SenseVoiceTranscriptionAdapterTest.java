package com.company.outbound.processing;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SenseVoiceTranscriptionAdapterTest {
    @TempDir Path tempDir;

    @Test
    void uploadsWavBytesAsMultipartInsteadOfPostingLocalFilename() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = "{\"text\":\"self confirmed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            Path recording = tempDir.resolve("answer.wav");
            Files.write(recording, new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4});
            var adapter = new SenseVoiceTranscriptionAdapter(url(server), new long[]{0});

            assertThat(adapter.transcribe(recording)).isEqualTo("self confirmed");
            assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
            assertThat(new String(body.get(), StandardCharsets.ISO_8859_1))
                .contains("name=\"file\"; filename=\"answer.wav\"")
                .contains("RIFF");
        } finally { server.stop(0); }
    }
    @Test
    void postsLocalFilenameAndReturnsText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(200, "{\"text\":\"我是本人\"}", body);
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server));
            assertThat(adapter.transcribe(recording("answer-q1.wav"))).isEqualTo("我是本人");
            assertThat(body.get()).contains("filename=\"answer-q1.wav\"");
        } finally { server.stop(0); }
    }

    @Test
    void rejectsEmptyRecognitionResult() throws Exception {
        HttpServer server = server(200, "{\"text\":\"  \"}", new AtomicReference<>());
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server));
            assertThatThrownBy(() -> adapter.transcribe(recording("answer.wav")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
        } finally { server.stop(0); }
    }

    @Test
    void retriesOnceWhenFirstResponseIsNotJson() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int attempt = requests.incrementAndGet();
            String response = attempt == 1
                ? "Invalid HTTP request received."
                : "{\"text\":\"是本人\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server));

            assertThat(adapter.transcribe(recording("answer.wav"))).isEqualTo("是本人");
            assertThat(requests).hasValue(2);
        } finally { server.stop(0); }
    }

    @Test
    void doesNotRetryBusinessError() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();
            byte[] bytes = "{\"code\":\"NO_SPEECH\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server));

            assertThatThrownBy(() -> adapter.transcribe(recording("answer.wav")))
                .hasMessageContaining("NO_SPEECH");
            assertThat(requests).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test
    void retriesUnknown422UntilRecordingIsReady() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int attempt = requests.incrementAndGet();
            String response = attempt < 3 ? "{\"detail\":\"recording not ready\"}" : "{\"text\":\"self confirmed\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(attempt < 3 ? 422 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server));

            assertThat(adapter.transcribe(recording("answer.wav"))).isEqualTo("self confirmed");
            assertThat(requests).hasValue(3);
        } finally { server.stop(0); }
    }

    @Test
    void allowsFourthAttemptForSlowRecordingReadiness() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int attempt = requests.incrementAndGet();
            String response = attempt < 4 ? "{\"detail\":\"recording not ready\"}" : "{\"text\":\"self confirmed\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(attempt < 4 ? 422 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new SenseVoiceTranscriptionAdapter(url(server), new long[]{0, 0, 0, 0});

            assertThat(adapter.transcribe(recording("answer.wav"))).isEqualTo("self confirmed");
            assertThat(requests).hasValue(4);
        } finally { server.stop(0); }
    }

    private static HttpServer server(int status, String response, AtomicReference<String> body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/transcriptions/upload", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private Path recording(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4});
        return path;
    }
}
