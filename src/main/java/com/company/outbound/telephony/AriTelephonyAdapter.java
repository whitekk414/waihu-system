package com.company.outbound.telephony;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "outbound.telephony.mode", havingValue = "ari")
class AriTelephonyAdapter implements TelephonyPort {
    private final String baseUrl;
    private final String authorization;
    private final String eventApiKey;
    private final HttpClient client;
    private final Path recordingsRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, UUID> channelTasks = new ConcurrentHashMap<>();
    private final Set<String> handledEventKeys = ConcurrentHashMap.newKeySet();
    private Consumer<TelephonyEvent> listener = ignored -> { };
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();

    @Autowired
    AriTelephonyAdapter(
        @Value("${outbound.telephony.ari.base-url}") String baseUrl,
        @Value("${outbound.telephony.ari.username}") String username,
        @Value("${outbound.telephony.ari.password}") String password,
        @Value("${outbound.telephony.recordings-root:recordings}") Path recordingsRoot) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.eventApiKey = username + ":" + password;
        this.recordingsRoot = recordingsRoot.toAbsolutePath().normalize();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    AriTelephonyAdapter(String baseUrl, String username, String password) {
        this(baseUrl, username, password, Path.of("recordings"));
    }

    @PostConstruct
    void startEventStream() {
        connectEventStream();
    }

    URI eventsUri() {
        String websocketBase = baseUrl.replaceFirst("^http://", "ws://")
            .replaceFirst("^https://", "wss://");
        return URI.create(websocketBase + "/ari/events?" + query(Map.of(
            "app", "waihu-dialog", "api_key", eventApiKey)));
    }

    private void connectEventStream() {
        reconnectScheduled.set(false);
        client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
            .buildAsync(eventsUri(), new AriWebSocketListener())
            .exceptionally(error -> { scheduleReconnect(); return null; });
    }

    private void scheduleReconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(this::connectEventStream,
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));
    }

    @Override
    public String originate(CallCommand command) {
        String query = query(Map.of(
            "endpoint", "Mobile/honor/" + command.extension(),
            "app", "waihu-dialog",
            "appArgs", command.taskId().toString()));
        JsonNode body = send("POST", "/ari/channels?" + query, true);
        String channelId = body.path("id").asText();
        if (channelId.isBlank()) throw new IllegalStateException("ARI originate returned no channel id");
        rememberChannel(channelId, command.taskId());
        return channelId;
    }

    @Override
    public String play(UUID taskId, String channelId, String promptId) {
        String playbackId = "play-" + UUID.randomUUID();
        rememberChannel(channelId, taskId);
        send("POST", "/ari/channels/" + encodePath(channelId) + "/play/" + encodePath(playbackId)
            + "?" + query(Map.of("media", "sound:custom/" + promptId)), false);
        return playbackId;
    }

    @Override
    public String record(UUID taskId, String channelId, String recordingName,
                         int maxDurationSeconds, int maxSilenceSeconds) {
        rememberChannel(channelId, taskId);
        send("POST", "/ari/channels/" + encodePath(channelId) + "/record?" + query(Map.of(
            "name", recordingName,
            "format", "wav",
            "maxDurationSeconds", Integer.toString(maxDurationSeconds),
            "maxSilenceSeconds", Integer.toString(maxSilenceSeconds),
            "ifExists", "fail",
            "beep", "false")), false);
        return recordingName;
    }

    @Override
    public void hangup(UUID taskId, String channelId) {
        rememberChannel(channelId, taskId);
        send("DELETE", "/ari/channels/" + encodePath(channelId), false);
    }

    @Override
    public void setEventListener(Consumer<TelephonyEvent> listener) {
        this.listener = listener == null ? ignored -> { } : listener;
    }

    void rememberChannel(String channelId, UUID taskId) {
        channelTasks.put(channelId, taskId);
    }

    void handleEventJson(String json) {
        try {
            JsonNode event = objectMapper.readTree(json);
            String ariType = event.path("type").asText();
            JsonNode resource;
            TelephonyEventType type;
            String operationId = null;
            String channelId;
            Path recording = null;
            switch (ariType) {
                case "ChannelStateChange" -> {
                    resource = event.path("channel");
                    if (!"Up".equals(resource.path("state").asText())) return;
                    type = TelephonyEventType.CHANNEL_ANSWERED;
                    channelId = resource.path("id").asText();
                }
                case "PlaybackFinished" -> {
                    resource = event.path("playback");
                    type = "failed".equalsIgnoreCase(resource.path("state").asText())
                        ? TelephonyEventType.OPERATION_FAILED
                        : TelephonyEventType.PLAYBACK_FINISHED;
                    operationId = resource.path("id").asText();
                    channelId = targetId(resource.path("target_uri").asText());
                }
                case "RecordingFinished" -> {
                    resource = event.path("recording");
                    type = TelephonyEventType.RECORDING_FINISHED;
                    operationId = resource.path("name").asText();
                    channelId = targetId(resource.path("target_uri").asText());
                    recording = downloadRecording(operationId);
                }
                case "StasisEnd" -> {
                    resource = event.path("channel");
                    type = TelephonyEventType.CHANNEL_ENDED;
                    channelId = resource.path("id").asText();
                }
                case "StasisStart" -> {
                    resource = event.path("channel");
                    channelId = resource.path("id").asText();
                    JsonNode args = event.path("args");
                    if (args.isArray() && !args.isEmpty()) {
                        try { rememberChannel(channelId, UUID.fromString(args.get(0).asText())); }
                        catch (IllegalArgumentException ignored) { }
                    }
                    if (!"Up".equals(resource.path("state").asText())) return;
                    type = TelephonyEventType.CHANNEL_ANSWERED;
                }
                default -> { return; }
            }
            UUID taskId = channelTasks.get(channelId);
            if (taskId == null) return;
            String key = ariType + '|' + (operationId == null ? channelId : operationId)
                + '|' + event.path("timestamp").asText();
            if (!handledEventKeys.add(key)) return;
            listener.accept(new TelephonyEvent(
                UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), taskId, type,
                channelId, operationId, ariType, recording));
            if (type == TelephonyEventType.CHANNEL_ENDED) channelTasks.remove(channelId);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid ARI event", error);
        }
    }

    private JsonNode send(String method, String path, boolean expectJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(10)).header("Authorization", authorization);
        if ("DELETE".equals(method)) builder.DELETE(); else builder.POST(HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Asterisk ARI request failed: HTTP " + response.statusCode());
            }
            return expectJson ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Asterisk ARI request interrupted", error);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot call Asterisk ARI", error);
        }
    }

    private Path downloadRecording(String recordingName) throws Exception {
        if (!recordingName.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Unsafe recording name");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl
                + "/ari/recordings/stored/" + encodePath(recordingName) + "/file"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", authorization)
            .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Cannot download Asterisk recording: HTTP " + response.statusCode());
        }
        Files.createDirectories(recordingsRoot);
        Path target = recordingsRoot.resolve(recordingName + ".wav").normalize();
        if (!target.startsWith(recordingsRoot)) throw new IllegalArgumentException("Unsafe recording target");
        Files.write(target, response.body());
        return target;
    }

    private static String targetId(String targetUri) {
        int separator = targetUri.indexOf(':');
        return separator < 0 ? targetUri : targetUri.substring(separator + 1);
    }

    private static String query(Map<String, String> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String value) {
        return encode(value);
    }

    private final class AriWebSocketListener implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String json = message.toString();
                message.setLength(0);
                try { handleEventJson(json); } catch (RuntimeException ignored) { }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            scheduleReconnect();
        }
    }
}
