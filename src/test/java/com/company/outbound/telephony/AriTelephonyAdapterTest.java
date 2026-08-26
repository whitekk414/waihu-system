package com.company.outbound.telephony;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class AriTelephonyAdapterTest {
    @TempDir Path tempDir;

    @Test
    void buildsAuthenticatedAriEventStreamUri() {
        AriTelephonyAdapter adapter = new AriTelephonyAdapter(
            "http://localhost:8088", "waihu", "p@ss word");

        assertThat(adapter.eventsUri().toString())
            .startsWith("ws://localhost:8088/ari/events?")
            .contains("app=waihu-dialog")
            .contains("api_key=waihu%3Ap%40ss%20word");
    }

    @Test
    void originateUsesHonorMobileAndStasisApplication() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ari/channels", exchange -> {
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"id\":\"channel-123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AriTelephonyAdapter adapter = new AriTelephonyAdapter(
                "http://localhost:" + server.getAddress().getPort(), "outbound", "secret");

            String channelId = adapter.originate(
                new CallCommand(UUID.randomUUID(), "1001", "payment-reminder"));

            assertThat(channelId).isEqualTo("channel-123");
            assertThat(method.get()).isEqualTo("POST");
            assertThat(query.get()).contains("endpoint=Mobile%2Fhonor%2F1001")
                .contains("app=waihu-dialog")
                .contains("appArgs=");
            assertThat(authorization.get()).startsWith("Basic ");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void formsPlaybackRecordingAndHangupRequests() throws Exception {
        List<String> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ari/channels/channel-1/play", exchange -> respond(exchange, requests));
        server.createContext("/ari/channels/channel-1/record", exchange -> respond(exchange, requests));
        server.createContext("/ari/channels/channel-1", exchange -> respond(exchange, requests));
        server.start();
        try {
            AriTelephonyAdapter adapter = new AriTelephonyAdapter(
                "http://localhost:" + server.getAddress().getPort(), "outbound", "secret");
            UUID taskId = UUID.randomUUID();

            String playbackId = adapter.play(taskId, "channel-1", "identity-question");
            String recordingId = adapter.record(taskId, "channel-1", "answer-q1", 20, 3);
            adapter.hangup(taskId, "channel-1");

            assertThat(playbackId).startsWith("play-");
            assertThat(recordingId).isEqualTo("answer-q1");
            assertThat(requests).anySatisfy(request -> assertThat(request)
                .startsWith("POST /ari/channels/channel-1/play/play-")
                .contains("media=sound%3Acustom%2Fidentity-question"));
            assertThat(requests).anySatisfy(request -> assertThat(request)
                .startsWith("POST /ari/channels/channel-1/record?")
                .contains("name=answer-q1")
                .contains("format=wav")
                .contains("maxDurationSeconds=20")
                .contains("maxSilenceSeconds=3")
                .contains("ifExists=fail")
                .contains("beep=false"));
            assertThat(requests).contains("DELETE /ari/channels/channel-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsAriMediaEventsAndDropsDuplicates() {
        AriTelephonyAdapter adapter = new AriTelephonyAdapter("http://localhost:1", "u", "p");
        List<TelephonyEvent> events = new ArrayList<>();
        adapter.setEventListener(events::add);
        UUID taskId = UUID.randomUUID();
        adapter.rememberChannel("channel-1", taskId);
        String json = """
            {"type":"PlaybackFinished","timestamp":"2026-08-04T07:00:00Z",
             "playback":{"id":"play-1","target_uri":"channel:channel-1"}}
            """;

        adapter.handleEventJson(json);
        adapter.handleEventJson(json);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(TelephonyEventType.PLAYBACK_FINISHED);
            assertThat(event.operationId()).isEqualTo("play-1");
            assertThat(event.channelId()).isEqualTo("channel-1");
            assertThat(event.taskId()).isEqualTo(taskId);
        });
    }

    @Test
    void mapsFailedPlaybackToOperationFailure() {
        AriTelephonyAdapter adapter = new AriTelephonyAdapter("http://localhost:1", "u", "p");
        List<TelephonyEvent> events = new ArrayList<>();
        adapter.setEventListener(events::add);
        UUID taskId = UUID.randomUUID();
        adapter.rememberChannel("channel-1", taskId);

        adapter.handleEventJson("""
            {"type":"PlaybackFinished","timestamp":"2026-08-04T07:00:00Z",
             "playback":{"id":"play-1","state":"failed","target_uri":"channel:channel-1"}}
            """);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(TelephonyEventType.OPERATION_FAILED);
            assertThat(event.operationId()).isEqualTo("play-1");
        });
    }

    @Test
    void mapsUpStasisStartToAnsweredWhenChannelMappingArrivesLate() {
        AriTelephonyAdapter adapter = new AriTelephonyAdapter("http://localhost:1", "u", "p");
        List<TelephonyEvent> events = new ArrayList<>();
        adapter.setEventListener(events::add);
        UUID taskId = UUID.randomUUID();

        adapter.handleEventJson("""
            {"type":"StasisStart","timestamp":"2026-08-12T09:52:51Z",
             "args":["%s"],
             "channel":{"id":"channel-late","state":"Up"}}
            """.formatted(taskId));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(TelephonyEventType.CHANNEL_ANSWERED);
            assertThat(event.channelId()).isEqualTo("channel-late");
            assertThat(event.taskId()).isEqualTo(taskId);
        });
    }

    @Test
    void downloadsFinishedRecordingBeforePublishingEvent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ari/recordings/stored/answer-q1/file", exchange -> {
            byte[] wav = new byte[]{'R','I','F','F',1,2,3,4};
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav);
            exchange.close();
        });
        server.start();
        try {
            AriTelephonyAdapter adapter = new AriTelephonyAdapter(
                "http://localhost:" + server.getAddress().getPort(), "u", "p", tempDir);
            List<TelephonyEvent> events = new ArrayList<>();
            UUID taskId = UUID.randomUUID();
            adapter.setEventListener(events::add);
            adapter.rememberChannel("channel-1", taskId);

            adapter.handleEventJson("""
                {"type":"RecordingFinished","timestamp":"2026-08-04T07:00:00Z",
                 "recording":{"name":"answer-q1","target_uri":"channel:channel-1"}}
                """);

            Path recording = tempDir.resolve("answer-q1.wav");
            assertThat(recording).exists();
            assertThat(Files.readAllBytes(recording)).startsWith('R','I','F','F');
            assertThat(events).singleElement().satisfies(event ->
                assertThat(event.recording()).isEqualTo(recording));
        } finally { server.stop(0); }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, List<String> requests)
        throws java.io.IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
