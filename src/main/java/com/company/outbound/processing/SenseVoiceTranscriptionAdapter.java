package com.company.outbound.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.Files;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "outbound.processing.transcription", havingValue = "sensevoice")
class SenseVoiceTranscriptionAdapter implements TranscriptionPort {
    private static final long[] DEFAULT_ATTEMPT_DELAYS_MILLIS = {1000, 1000, 2000, 4000};
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final long[] attemptDelaysMillis;

    @Autowired
    SenseVoiceTranscriptionAdapter(
        @Value("${outbound.processing.sensevoice.base-url:http://127.0.0.1:8090}") String baseUrl) {
        this(baseUrl, DEFAULT_ATTEMPT_DELAYS_MILLIS);
    }

    SenseVoiceTranscriptionAdapter(String baseUrl, long[] attemptDelaysMillis) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.attemptDelaysMillis = attemptDelaysMillis.clone();
        if (this.attemptDelaysMillis.length == 0) {
            throw new IllegalArgumentException("At least one transcription attempt is required");
        }
    }

    @Override
    public String transcribe(Path recording) {
        RetryableTranscriptionException firstFailure = null;
        for (int attempt = 0; attempt < attemptDelaysMillis.length; attempt++) {
            waitBeforeAttempt(attemptDelaysMillis[attempt]);
            try {
                return transcribeOnce(recording);
            } catch (RetryableTranscriptionException error) {
                if (firstFailure == null) firstFailure = error;
                if (attempt == attemptDelaysMillis.length - 1) {
                    if (error != firstFailure) error.addSuppressed(firstFailure);
                    throw error;
                }
            }
        }
        throw new IllegalStateException("Unreachable transcription retry state");
    }

    private String transcribeOnce(Path recording) {
        try {
            String boundary = "----waihu-" + UUID.randomUUID();
            byte[] audio = Files.readAllBytes(recording);
            byte[] prefix = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + recording.getFileName() + "\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/v1/transcriptions/upload"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofByteArray(audio),
                    HttpRequest.BodyPublishers.ofByteArray(suffix)))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body;
            try {
                body = response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
            } catch (JsonProcessingException error) {
                throw new RetryableTranscriptionException("SenseVoice returned non-JSON response", error);
            }
            if (response.statusCode() >= 500) {
                throw new RetryableTranscriptionException(
                    "SenseVoice request failed: HTTP " + response.statusCode());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String code = body.path("code").asText("UNKNOWN");
                if (response.statusCode() == 422 && "UNKNOWN".equals(code)) {
                    throw new RetryableTranscriptionException(
                        "SenseVoice recording is not ready: HTTP 422");
                }
                throw new IllegalStateException(
                    "SenseVoice request failed: HTTP " + response.statusCode() + " code=" + code);
            }
            String text = body.path("text").asText().trim();
            if (text.isEmpty()) throw new RetryableTranscriptionException("SenseVoice returned empty text");
            return text;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SenseVoice request interrupted", error);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new RetryableTranscriptionException("Cannot call SenseVoice", error);
        }
    }

    private static void waitBeforeAttempt(long delayMillis) {
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SenseVoice retry interrupted", error);
        }
    }

    private static final class RetryableTranscriptionException extends IllegalStateException {
        RetryableTranscriptionException(String message) { super(message); }
        RetryableTranscriptionException(String message, Throwable cause) { super(message, cause); }
    }
}
