# Free SIP Outbound Call PoC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a locally runnable Spring Boot and Asterisk prototype that calls a SIP test extension, plays a fixed prompt, records the response, and displays transcription and GPT analysis progress in a live web page.

**Architecture:** One Java 17 Spring Boot application owns tasks, state transitions, SSE events, adapter interfaces, recording processing, and the test UI. Asterisk runs in a locally built Docker container and exposes SIP/RTP plus ARI; mock adapters make the application testable before Asterisk or model credentials are available.

**Tech Stack:** Java 17, Maven 3.6+, Spring Boot 3.4.x, Spring Web, Spring Data JPA, H2, Bean Validation, Jackson JSON Schema-style validation, JUnit 5, MockMvc, Testcontainers-free Docker Compose, Asterisk/PJSIP/ARI, HTML/CSS/vanilla JavaScript, SSE.

**Execution constraint:** Do not create Git commits or push changes. The user requested local files only.

---

## File map

- `pom.xml`: Maven build, runtime, and test dependencies.
- `src/main/java/com/company/outbound/OutboundApplication.java`: application entry point.
- `src/main/java/com/company/outbound/task/*`: task entity, statuses, state machine, repository, service, DTOs, and REST controller.
- `src/main/java/com/company/outbound/events/*`: persisted task events and SSE publication.
- `src/main/java/com/company/outbound/telephony/*`: telephony port, mock implementation, ARI implementation, and ARI event mapping.
- `src/main/java/com/company/outbound/processing/*`: recording, transcription, analysis ports, mock adapters, schema validation, and orchestration.
- `src/main/resources/static/*`: no-login test page.
- `src/main/resources/application.yml`: safe local defaults and profile selection.
- `src/test/java/com/company/outbound/**/*`: unit and integration tests.
- `infra/asterisk/*`: reproducible Asterisk container and PJSIP/ARI/dialplan configuration.
- `docker-compose.yml`: local Asterisk service and exposed SIP/RTP/ARI ports.
- `.env.example`: non-secret configuration names.
- `README.md`: exact startup, SIP client, and test instructions.

### Task 1: Scaffold a healthy Spring Boot application

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/company/outbound/OutboundApplication.java`
- Create: `src/main/java/com/company/outbound/system/HealthController.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/company/outbound/system/HealthControllerTest.java`

- [ ] **Step 1: Write the failing health endpoint test**

```java
@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void returnsOk() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run: `mvn -Dtest=HealthControllerTest test`

Expected: Maven fails because the project classes do not exist yet.

- [ ] **Step 3: Add the Maven build and minimal application**

Use Spring Boot parent `3.4.7`, Java `17`, and dependencies `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `h2`, `spring-boot-starter-websocket`, and `spring-boot-starter-test`. Implement:

```java
@SpringBootApplication
public class OutboundApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutboundApplication.class, args);
    }
}

@RestController
class HealthController {
    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

Configure H2 file persistence at `./data/outbound`, schema update mode, port `8080`, UTF-8, and default adapter mode `mock` in `application.yml`.

- [ ] **Step 4: Run all tests**

Run: `mvn test`

Expected: `BUILD SUCCESS`, one passing test.

### Task 2: Implement the task state machine and persistence

**Files:**
- Create: `src/main/java/com/company/outbound/task/CallStatus.java`
- Create: `src/main/java/com/company/outbound/task/CallTask.java`
- Create: `src/main/java/com/company/outbound/task/CallTaskRepository.java`
- Create: `src/main/java/com/company/outbound/task/CallStateMachine.java`
- Test: `src/test/java/com/company/outbound/task/CallStateMachineTest.java`

- [ ] **Step 1: Write state transition tests**

```java
@Test void allowsExpectedTransition() {
    assertThat(machine.canMove(PENDING, VALIDATING)).isTrue();
}

@Test void rejectsSkippingDirectlyToCompleted() {
    assertThat(machine.canMove(PENDING, COMPLETED)).isFalse();
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=CallStateMachineTest test`

Expected: compilation failure because the status and state machine are missing.

- [ ] **Step 3: Implement statuses, allowed transitions, and entity**

Define the exact enum values from the spec: `PENDING`, `VALIDATING`, `ORIGINATING`, `RINGING`, `ANSWERED`, `PLAYING_PROMPT`, `WAITING_RESPONSE`, `CALL_ENDED`, `RECORDING_READY`, `TRANSCRIBING`, `ANALYZING`, `COMPLETED`, `FAILED`.

Implement immutable transition sets, including failure from every nonterminal state and retry from `FAILED` to `VALIDATING`. `CallTask` stores UUID, extension, prompt ID, status, ARI channel ID, timestamps, last error, retry count, and optimistic `@Version`.

- [ ] **Step 4: Run state tests and full tests**

Run: `mvn -Dtest=CallStateMachineTest test; mvn test`

Expected: both commands succeed.

### Task 3: Add task creation, events, SSE, and queries

**Files:**
- Create: `src/main/java/com/company/outbound/task/CreateCallRequest.java`
- Create: `src/main/java/com/company/outbound/task/CallTaskView.java`
- Create: `src/main/java/com/company/outbound/task/CallTaskService.java`
- Create: `src/main/java/com/company/outbound/task/CallTaskController.java`
- Create: `src/main/java/com/company/outbound/events/TaskEvent.java`
- Create: `src/main/java/com/company/outbound/events/TaskEventRepository.java`
- Create: `src/main/java/com/company/outbound/events/TaskEventService.java`
- Create: `src/main/java/com/company/outbound/events/TaskEventController.java`
- Test: `src/test/java/com/company/outbound/task/CallTaskControllerTest.java`

- [ ] **Step 1: Write API tests**

```java
@Test void createsAndReadsTask() throws Exception {
    String body = mvc.perform(post("/api/tasks")
            .contentType(APPLICATION_JSON)
            .content("{\"extension\":\"1001\",\"promptId\":\"payment-reminder\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(body).get("id").asText();
    mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isOk());
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=CallTaskControllerTest test`

Expected: endpoint not found or compilation failure.

- [ ] **Step 3: Implement REST, event persistence, and SSE registry**

Expose `POST /api/tasks`, `GET /api/tasks`, `GET /api/tasks/{id}`, `POST /api/tasks/{id}/retry`, and `GET /api/events/stream`. Validate extension with `^[0-9]{2,20}$` and prompt ID with `^[a-z0-9-]{1,64}$`. Persist an event for every transition, publish it to active `SseEmitter` clients, use a 30-minute timeout, remove completed emitters, and send a heartbeat every 15 seconds.

- [ ] **Step 4: Run controller and full tests**

Run: `mvn -Dtest=CallTaskControllerTest test; mvn test`

Expected: task creation returns 201 and all tests pass.

### Task 4: Add a mock telephony call lifecycle

**Files:**
- Create: `src/main/java/com/company/outbound/telephony/TelephonyPort.java`
- Create: `src/main/java/com/company/outbound/telephony/CallCommand.java`
- Create: `src/main/java/com/company/outbound/telephony/TelephonyEvent.java`
- Create: `src/main/java/com/company/outbound/telephony/MockTelephonyAdapter.java`
- Create: `src/main/java/com/company/outbound/telephony/CallOrchestrator.java`
- Test: `src/test/java/com/company/outbound/telephony/CallOrchestratorTest.java`

- [ ] **Step 1: Write orchestration tests with a fake port**

```java
@Test void startsPendingTaskExactlyOnce() {
    orchestrator.start(taskId);
    assertThat(fake.commands()).containsExactly(new CallCommand(taskId, "1001", "payment-reminder"));
    assertThat(taskService.get(taskId).status()).isEqualTo(ORIGINATING);
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=CallOrchestratorTest test`

Expected: compilation failure because telephony abstractions are missing.

- [ ] **Step 3: Implement the port and deterministic mock lifecycle**

`TelephonyPort.originate(CallCommand)` returns a channel ID. The mock adapter schedules `RINGING`, `ANSWERED`, `PLAYING_PROMPT`, `WAITING_RESPONSE`, `CALL_ENDED`, and a recording-ready event with configurable short delays. `CallOrchestrator` converts events into validated state transitions and ignores duplicate events by event ID.

- [ ] **Step 4: Run focused and full tests**

Run: `mvn -Dtest=CallOrchestratorTest test; mvn test`

Expected: one originate command and a valid lifecycle.

### Task 5: Implement recording, transcription, and analysis pipeline

**Files:**
- Create: `src/main/java/com/company/outbound/processing/RecordingService.java`
- Create: `src/main/java/com/company/outbound/processing/TranscriptionPort.java`
- Create: `src/main/java/com/company/outbound/processing/AnalysisPort.java`
- Create: `src/main/java/com/company/outbound/processing/MockTranscriptionAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/MockAnalysisAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/AnalysisResult.java`
- Create: `src/main/java/com/company/outbound/processing/ProcessingOrchestrator.java`
- Test: `src/test/java/com/company/outbound/processing/ProcessingOrchestratorTest.java`

- [ ] **Step 1: Write the pipeline test**

```java
@Test void turnsRecordingIntoCompletedAnalysis() {
    orchestrator.process(taskId, Path.of("src/test/resources/sample.wav"));
    CallTaskView result = taskService.get(taskId);
    assertThat(result.status()).isEqualTo(COMPLETED);
    assertThat(result.analysis().needsHumanReview()).isTrue();
}
```

- [ ] **Step 2: Add a valid one-second silence WAV test fixture and confirm failure**

Generate it with PowerShell/.NET or a checked-in binary fixture, then run: `mvn -Dtest=ProcessingOrchestratorTest test`.

Expected: compilation failure because processing classes are missing.

- [ ] **Step 3: Implement adapters and orchestration**

Validate that the file exists, has a `.wav` extension, is larger than 44 bytes, and compute SHA-256. Transition through `RECORDING_READY`, `TRANSCRIBING`, and `ANALYZING`. The mock transcription returns a deterministic Chinese test transcript. The mock analysis returns a typed `AnalysisResult` with all fields from the design JSON and persists both transcript and JSON on the task.

- [ ] **Step 4: Run focused and full tests**

Run: `mvn -Dtest=ProcessingOrchestratorTest test; mvn test`

Expected: task completes with persisted transcript and typed result.

### Task 6: Build the no-login live test page

**Files:**
- Create: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/app.js`
- Create: `src/main/resources/static/styles.css`
- Test: `src/test/java/com/company/outbound/system/StaticPageTest.java`

- [ ] **Step 1: Write a static page smoke test**

```java
@Test void servesTestConsole() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("SIP 外呼链路测试")));
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=StaticPageTest test`

Expected: 404 or missing expected title.

- [ ] **Step 3: Implement the page**

Create an extension field, prompt selector, create button, connection badges, task table, selected-task timeline, recording player, transcript area, analysis cards, error area, and retry button. `app.js` loads `/api/tasks`, subscribes to `/api/events/stream`, reloads affected tasks on events, reconnects SSE with capped exponential delay, and never uses `innerHTML` for external text.

- [ ] **Step 4: Run tests and manually smoke-test mock mode**

Run: `mvn test; mvn spring-boot:run`

Expected: tests pass; `http://localhost:8080` creates a task and visibly progresses to `COMPLETED` without page refresh.

### Task 7: Add reproducible Asterisk infrastructure

**Files:**
- Create: `infra/asterisk/Dockerfile`
- Create: `infra/asterisk/asterisk.conf`
- Create: `infra/asterisk/pjsip.conf`
- Create: `infra/asterisk/extensions.conf`
- Create: `infra/asterisk/http.conf`
- Create: `infra/asterisk/ari.conf`
- Create: `infra/asterisk/rtp.conf`
- Create: `infra/asterisk/prompts/payment-reminder.wav`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Test: `scripts/check-asterisk.ps1`

- [ ] **Step 1: Add a failing infrastructure check**

The script requests `http://localhost:8088/ari/asterisk/info` with basic authentication, fails on a non-2xx response, and verifies UDP port declarations `5060` and `10000-10100` exist in `docker-compose.yml`.

- [ ] **Step 2: Run and confirm failure**

Run: `powershell -ExecutionPolicy Bypass -File scripts/check-asterisk.ps1`

Expected: connection failure because Asterisk is not running.

- [ ] **Step 3: Implement the container and configuration**

Build from `debian:bookworm-slim`, install the Debian Asterisk package, copy local configuration, and run `asterisk -f -vvv`. Configure PJSIP extension `1001` from environment-provided secret, ARI user from environment, HTTP on `8088`, SIP UDP `5060`, RTP UDP `10000-10100`, and a dialplan context that can answer, record, playback `payment-reminder`, wait for response, and hang up.

- [ ] **Step 4: Start Docker Desktop, build Asterisk, and run the check**

Run: `docker compose up -d --build; powershell -ExecutionPolicy Bypass -File scripts/check-asterisk.ps1`

Expected: container is healthy and ARI returns Asterisk information.

### Task 8: Implement the real ARI telephony adapter

**Files:**
- Create: `src/main/java/com/company/outbound/telephony/AriProperties.java`
- Create: `src/main/java/com/company/outbound/telephony/AriHttpClient.java`
- Create: `src/main/java/com/company/outbound/telephony/AriEventClient.java`
- Create: `src/main/java/com/company/outbound/telephony/AriTelephonyAdapter.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/company/outbound/telephony/AriTelephonyAdapterTest.java`

- [ ] **Step 1: Write HTTP contract tests against a local mock server**

Verify originate uses `POST /ari/channels`, includes endpoint `PJSIP/1001`, app name, caller ID, and task ID variables; verify duplicate WebSocket events are ignored and `StasisStart`, channel state changes, playback completion, recording completion, and `StasisEnd` map to the exact domain events.

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=AriTelephonyAdapterTest test`

Expected: compilation failure because ARI classes are missing.

- [ ] **Step 3: Implement ARI HTTP/WebSocket control**

Use Java `HttpClient` for authenticated REST and WebSocket calls. Bind configuration under `outbound.telephony.ari`, redact credentials from errors, set connection/request timeouts, reconnect event WebSocket with capped backoff, and activate this adapter only when `outbound.telephony.mode=ari`.

- [ ] **Step 4: Run automated tests and a real SIP call**

Run: `mvn test; $env:OUTBOUND_TELEPHONY_MODE='ari'; mvn spring-boot:run`

Expected: the registered SIP client rings, hears the fixed prompt after answer, and Asterisk produces a WAV recording.

### Task 9: Add real provider adapter configuration without embedding secrets

**Files:**
- Create: `src/main/java/com/company/outbound/processing/HttpTranscriptionAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/HttpAnalysisAdapter.java`
- Create: `src/main/java/com/company/outbound/processing/ProviderProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Test: `src/test/java/com/company/outbound/processing/HttpProviderAdaptersTest.java`

- [ ] **Step 1: Write provider contract tests with a local mock HTTP server**

Assert multipart WAV upload, bearer authentication, timeouts, non-2xx error mapping, transcript extraction, analysis request construction, and complete `AnalysisResult` parsing. Test that logs and exceptions never contain the API key.

- [ ] **Step 2: Run and confirm failure**

Run: `mvn -Dtest=HttpProviderAdaptersTest test`

Expected: compilation failure because HTTP provider adapters are missing.

- [ ] **Step 3: Implement generic configurable HTTP adapters**

Read base URL, endpoint paths, model identifiers, API key, and timeout from environment-backed properties. Keep provider-specific JSON mapping inside the adapters. Activate them only when `outbound.processing.mode=http`; otherwise retain deterministic mock adapters.

- [ ] **Step 4: Run contract and full tests**

Run: `mvn -Dtest=HttpProviderAdaptersTest test; mvn test`

Expected: all provider contracts and all application tests pass without external network access.

### Task 10: End-to-end verification and operator documentation

**Files:**
- Create: `scripts/run-local.ps1`
- Create: `scripts/stop-local.ps1`
- Create: `scripts/e2e-smoke.ps1`
- Create: `README.md`
- Modify: `docs/superpowers/plans/2026-07-21-free-sip-outbound-poc-implementation.md`

- [ ] **Step 1: Implement an automated mock smoke test**

The script waits for `/api/health`, creates one task, polls it until terminal status, fails unless status is `COMPLETED`, and verifies transcript and analysis fields are present.

- [ ] **Step 2: Run the mock end-to-end smoke test**

Run: `powershell -ExecutionPolicy Bypass -File scripts/run-local.ps1; powershell -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1`

Expected: one task completes and the script exits 0.

- [ ] **Step 3: Document exact setup and real SIP verification**

Document prerequisites, Docker Desktop startup, environment copy, Asterisk startup, SIP client account `1001` configuration, Windows LAN IP selection, firewall ports, Spring profiles, mock test, real SIP call, recording location, provider variables, troubleshooting, shutdown, and the future VoLTE gateway replacement point.

- [ ] **Step 4: Execute the final verification matrix**

Run: `mvn clean test; docker compose config; powershell -ExecutionPolicy Bypass -File scripts/check-asterisk.ps1; powershell -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1`.

Expected: Maven succeeds, Compose validates, ARI is healthy, and the mock task completes. Then manually perform five SIP calls covering normal answer, no answer, offline extension, transcription retry, and analysis retry; record observed results in the plan checkboxes without adding secrets or customer data.

