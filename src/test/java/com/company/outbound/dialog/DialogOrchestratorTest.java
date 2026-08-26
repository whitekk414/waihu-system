package com.company.outbound.dialog;

import com.company.outbound.processing.DecisionPort;
import com.company.outbound.processing.TranscriptionPort;
import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.telephony.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DialogOrchestratorTest {
    @Test
    void guidedModeProcessesEveryAnswerAndSelectsAuditedTransitions() {
        FakeTelephony telephony = new FakeTelephony();
        TranscriptionPort transcription = mock(TranscriptionPort.class);
        when(transcription.transcribe(any())).thenReturn("是的，我是本人", "可以，我配合", "周五下午三点");
        CallTaskService tasks = mock(CallTaskService.class);
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, transcription, mock(DecisionPort.class), mock(DialogSessionService.class), tasks,
            true, List.of("identity-question", "visit-consent-question", "contact-time-question", "closing"));
        UUID taskId = UUID.randomUUID();

        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        for (int answer = 0; answer < 3; answer++) {
            telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);
            telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED, "channel-1", telephony.lastOperation, Path.of("answer-" + answer + ".wav"));
            telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);
            if (answer < 2) telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);
        }
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);

        assertThat(telephony.actions).containsExactly(
            "originate:1001", "play:identity-question", "record:" + taskId + "-answer-1-a0", "play:processing",
            "play:identity-confirmed", "play:visit-consent-question", "record:" + taskId + "-answer-2-a0", "play:processing",
            "play:visit-accepted", "play:contact-time-question", "record:" + taskId + "-answer-3-a0", "play:processing",
            "play:closing", "hangup");
        verify(transcription, times(3)).transcribe(any());
        verify(tasks).completeGuidedDialog(eq(taskId), contains("周五下午三点"), contains("COMPLETED"));
    }

    @Test
    void guidedModeRetriesAnUnclearIdentityOnlyOnceThenMarksManualFollowUp() {
        FakeTelephony telephony = new FakeTelephony();
        TranscriptionPort transcription = mock(TranscriptionPort.class);
        when(transcription.transcribe(any())).thenReturn("你是谁", "听不懂");
        CallTaskService tasks = mock(CallTaskService.class);
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, transcription, mock(DecisionPort.class), mock(DialogSessionService.class), tasks,
            true, List.of("identity-question", "visit-consent-question", "contact-time-question", "closing"));
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        for (int attempt = 0; attempt < 2; attempt++) {
            telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);
            telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED, "channel-1", telephony.lastOperation, Path.of("unclear-" + attempt + ".wav"));
            telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);
        }
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", telephony.lastOperation, null);

        assertThat(telephony.actions).containsSubsequence("play:processing", "play:identity-retry",
            "record:" + taskId + "-answer-1-a1", "play:processing", "play:manual-closing", "hangup");
        verify(tasks).completeGuidedDialog(eq(taskId), anyString(), contains("\"needManualFollowUp\":true"));
    }

    @Test
    void completesTwoIndependentTurnsAndHangsUp() {
        FakeTelephony telephony = new FakeTelephony();
        TranscriptionPort transcription = recording -> recording.toString().contains("identity")
            ? "是我本人" : "我计划月底还款";
        DecisionPort decisions = (node, text) -> node == DialogNode.ASK_IDENTITY
            ? new DialogDecision(DialogIntent.SELF_CONFIRMED, .95,
                DialogNode.ASK_PAYMENT_PLAN, false, "本人")
            : new DialogDecision(DialogIntent.HAS_PAYMENT_PLAN, .92,
                DialogNode.COMPLETED, false, "月底还款");
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString()))
            .thenReturn(UUID.randomUUID(), UUID.randomUUID());
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, transcription, decisions, sessions, mock(CallTaskService.class));
        UUID taskId = UUID.randomUUID();

        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);
        telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED,
            "channel-1", telephony.lastOperation, Path.of("identity-answer.wav"));
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);
        telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED,
            "channel-1", telephony.lastOperation, Path.of("payment-answer.wav"));
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);

        assertThat(telephony.actions).containsExactly(
            "originate:1001", "play:identity-question", "record:" + taskId + "-identity-answer-a0",
            "play:payment-question", "record:" + taskId + "-payment-answer-a0", "play:closing", "hangup");
        verify(sessions, times(2)).startTurn(eq(taskId), any(), eq(0), anyString());
        verify(sessions, times(2)).completeTurn(any(), anyString(), any(), any());
    }

    @Test
    void ignoresDuplicatePlaybackEvent() {
        FakeTelephony telephony = new FakeTelephony();
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, path -> "", (node, text) -> null, sessions, mock(CallTaskService.class));
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        String playback = telephony.lastOperation;

        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", playback, null);
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, "channel-1", playback, null);

        assertThat(telephony.actions).filteredOn(action -> action.startsWith("record:"))
            .containsExactly("record:" + taskId + "-identity-answer-a0");
    }

    @Test
    void ignoresDuplicateAnsweredEventForSameChannel() {
        FakeTelephony telephony = new FakeTelephony();
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, path -> "", (node, text) -> null, sessions, mock(CallTaskService.class));
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");

        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);

        verify(sessions, times(1)).startTurn(taskId, DialogNode.ASK_IDENTITY, 0, "identity-question");
        assertThat(telephony.actions).containsExactly("originate:1001", "play:identity-question");
    }

    @Test
    void hangsUpAndCleansSessionWhenTranscriptionFails() {
        FakeTelephony telephony = new FakeTelephony();
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        CallTaskService tasks = mock(CallTaskService.class);
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony,
            path -> { throw new IllegalStateException("speech unavailable"); },
            (node, text) -> null,
            sessions, tasks);
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);

        telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED,
            "channel-1", telephony.lastOperation, Path.of("identity-answer.wav"));

        assertThat(telephony.actions).endsWith("hangup");
        verify(tasks).transition(taskId, CallStatus.FAILED, "speech unavailable");
        orchestrator.start(taskId, "1001");
        assertThat(telephony.actions).endsWith("originate:1001");
    }

    @Test
    void cleansSessionEvenWhenHangupFails() {
        FakeTelephony telephony = new FakeTelephony() {
            @Override
            public void hangup(UUID taskId, String channelId) {
                actions.add("hangup");
                throw new IllegalStateException("channel already gone");
            }
        };
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, path -> "", (node, text) -> null, sessions, mock(CallTaskService.class));
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");

        telephony.emit(taskId, TelephonyEventType.OPERATION_FAILED, "channel-1", null, null);

        orchestrator.start(taskId, "1001");
        assertThat(telephony.actions).endsWith("originate:1001");
    }

    @Test
    void hangsUpAndCleansSessionWhenDecisionFails() {
        FakeTelephony telephony = new FakeTelephony();
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony,
            path -> "是本人",
            (node, text) -> { throw new IllegalArgumentException("invalid model JSON"); },
            sessions, mock(CallTaskService.class));
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);

        telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED,
            "channel-1", telephony.lastOperation, Path.of("identity-answer.wav"));

        assertThat(telephony.actions).endsWith("hangup");
        orchestrator.start(taskId, "1001");
        assertThat(telephony.actions).endsWith("originate:1001");
    }

    @Test
    void truncatesLongFailureReasonBeforePersistingTaskStatus() {
        FakeTelephony telephony = new FakeTelephony();
        DialogSessionService sessions = mock(DialogSessionService.class);
        when(sessions.startTurn(any(), any(), anyInt(), anyString())).thenReturn(UUID.randomUUID());
        CallTaskService tasks = mock(CallTaskService.class);
        String longReason = "x".repeat(600);
        DialogOrchestrator orchestrator = new DialogOrchestrator(
            telephony, path -> { throw new IllegalStateException(longReason); },
            (node, text) -> null, sessions, tasks);
        UUID taskId = UUID.randomUUID();
        orchestrator.start(taskId, "1001");
        telephony.emit(taskId, TelephonyEventType.CHANNEL_ANSWERED, "channel-1", null, null);
        telephony.emit(taskId, TelephonyEventType.PLAYBACK_FINISHED,
            "channel-1", telephony.lastOperation, null);

        telephony.emit(taskId, TelephonyEventType.RECORDING_FINISHED,
            "channel-1", telephony.lastOperation, Path.of("identity-answer.wav"));

        verify(tasks).transition(eq(taskId), eq(CallStatus.FAILED), argThat(reason -> reason.length() <= 256));
    }

    static class FakeTelephony implements TelephonyPort {
        final List<String> actions = new ArrayList<>();
        Consumer<TelephonyEvent> listener = ignored -> {};
        String lastOperation;

        public String originate(CallCommand command) {
            actions.add("originate:" + command.extension());
            return "channel-1";
        }
        public String play(UUID taskId, String channelId, String promptId) {
            lastOperation = "play-" + actions.size(); actions.add("play:" + promptId); return lastOperation;
        }
        public String record(UUID taskId, String channelId, String name, int duration, int silence) {
            lastOperation = name; actions.add("record:" + name); return name;
        }
        public void hangup(UUID taskId, String channelId) { actions.add("hangup"); }
        public void setEventListener(Consumer<TelephonyEvent> listener) { this.listener = listener; }
        void emit(UUID taskId, TelephonyEventType type, String channelId, String operationId, Path path) {
            listener.accept(new TelephonyEvent(UUID.randomUUID(), taskId, type,
                channelId, operationId, type.name(), path));
        }
    }
}
