package com.company.outbound.dialog;

import com.company.outbound.processing.DecisionPort;
import com.company.outbound.processing.TranscriptionPort;
import com.company.outbound.telephony.*;
import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

@Service
@ConditionalOnProperty(name = "outbound.dialog.enabled", havingValue = "true")
public class DialogOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(DialogOrchestrator.class);
    private final TelephonyPort telephony;
    private final TranscriptionPort transcription;
    private final DecisionPort decisions;
    private final DialogSessionService sessions;
    private final CallTaskService tasks;
    private final boolean fixedPromptMode;
    private final List<String> fixedPrompts;
    private final DialogStateMachine stateMachine = new DialogStateMachine();
    private final GuidedAnswerClassifier guidedClassifier = new GuidedAnswerClassifier();
    private final Map<UUID, Session> active = new ConcurrentHashMap<>();

    @Autowired
    public DialogOrchestrator(TelephonyPort telephony, TranscriptionPort transcription,
                              DecisionPort decisions, DialogSessionService sessions,
                              CallTaskService tasks,
                              @Value("${outbound.dialog.fixed-prompt.enabled:false}") boolean fixedPromptMode,
                              @Value("${outbound.dialog.fixed-prompt.playlist:identity-question,payment-question,closing}") String fixedPromptPlaylist) {
        this(telephony, transcription, decisions, sessions, tasks, fixedPromptMode,
            Arrays.stream(fixedPromptPlaylist.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList());
    }

    DialogOrchestrator(TelephonyPort telephony, TranscriptionPort transcription,
                       DecisionPort decisions, DialogSessionService sessions,
                       CallTaskService tasks, boolean fixedPromptMode, List<String> fixedPrompts) {
        this.telephony = telephony;
        this.transcription = transcription;
        this.decisions = decisions;
        this.sessions = sessions;
        this.tasks = tasks;
        this.fixedPromptMode = fixedPromptMode;
        this.fixedPrompts = List.copyOf(fixedPrompts);
        if (fixedPromptMode && this.fixedPrompts.isEmpty()) {
            throw new IllegalArgumentException("Fixed prompt playlist must not be empty");
        }
        this.telephony.setEventListener(this::onEvent);
    }

    DialogOrchestrator(TelephonyPort telephony, TranscriptionPort transcription,
                       DecisionPort decisions, DialogSessionService sessions,
                       CallTaskService tasks) {
        this(telephony, transcription, decisions, sessions, tasks, false, List.of());
    }

    public void start(UUID taskId, String number) {
        Session session = new Session(taskId);
        if (active.putIfAbsent(taskId, session) != null) {
            throw new IllegalStateException("Dialog task is already active");
        }
        try {
            session.channelId = telephony.originate(new CallCommand(taskId, number, "identity-question"));
        } catch (RuntimeException error) {
            active.remove(taskId);
            throw error;
        }
    }

    private void onEvent(TelephonyEvent event) {
        Session session = active.get(event.taskId());
        if (session == null) return;
        synchronized (session) {
            if (session.terminal) return;
            if (session.channelId == null) session.channelId = event.channelId();
            if (event.channelId() != null && !event.channelId().equals(session.channelId)) return;
            try {
                switch (event.type()) {
                    case CHANNEL_RINGING -> { }
                    case CHANNEL_ANSWERED -> {
                        if (session.expectedType == null) {
                            if (fixedPromptMode) beginFixedPromptCall(session);
                            else beginTurn(session, DialogNode.ASK_IDENTITY, 0);
                        }
                    }
                    case PLAYBACK_FINISHED -> {
                        if (fixedPromptMode) fixedPlaybackFinished(session, event);
                        else playbackFinished(session, event);
                    }
                    case RECORDING_FINISHED -> {
                        if (fixedPromptMode) fixedRecordingFinished(session, event);
                        else recordingFinished(session, event);
                    }
                    case CHANNEL_ENDED -> finish(session);
                    case OPERATION_FAILED -> abort(session, event.message());
                }
            } catch (RuntimeException error) {
                log.error("Dialog event failed taskId={} node={} eventType={} reason={}",
                    session.taskId, session.node, event.type(), error.getClass().getSimpleName(), error);
                abort(session, error.getMessage());
            }
        }
    }

    private void beginFixedPromptCall(Session session) {
        session.fixedPromptIndex = 0;
        session.guidedPhase = GuidedPhase.QUESTION;
        playFixedPrompt(session);
    }

    private void playFixedPrompt(Session session) {
        session.expectedType = TelephonyEventType.PLAYBACK_FINISHED;
        session.expectedOperation = telephony.play(session.taskId, session.channelId,
            fixedPrompts.get(session.fixedPromptIndex));
    }

    private void fixedPlaybackFinished(Session session, TelephonyEvent event) {
        if (!expected(session, event, TelephonyEventType.PLAYBACK_FINISHED)) return;
        switch (session.guidedPhase) {
            case QUESTION, RETRY -> recordFixedAnswer(session);
            case PROCESSING -> processFixedAnswer(session);
            case RESPONSE -> {
                session.fixedPromptIndex++;
                session.fixedAttempt = 0;
                session.guidedPhase = GuidedPhase.QUESTION;
                playFixedPrompt(session);
            }
            case CLOSING -> completeFixedDialog(session);
        }
    }

    private void fixedRecordingFinished(Session session, TelephonyEvent event) {
        if (!expected(session, event, TelephonyEventType.RECORDING_FINISHED)) return;
        Path recording = event.recording() == null ? Path.of(event.operationId() + ".wav") : event.recording();
        session.pendingRecording = recording;
        session.guidedPhase = GuidedPhase.PROCESSING;
        playGuidedPrompt(session, "processing");
    }

    private void recordFixedAnswer(Session session) {
        String recordingName = session.taskId + "-answer-" + (session.fixedPromptIndex + 1) + "-a" + session.fixedAttempt;
        session.expectedType = TelephonyEventType.RECORDING_FINISHED;
        session.expectedOperation = telephony.record(session.taskId, session.channelId, recordingName, 15, 2);
    }

    private void processFixedAnswer(Session session) {
        String transcript = transcription.transcribe(session.pendingRecording);
        session.transcripts.add(transcript);
        GuidedIntent intent = switch (session.fixedPromptIndex) {
            case 0 -> guidedClassifier.identity(transcript);
            case 1 -> guidedClassifier.visitConsent(transcript);
            default -> guidedClassifier.contactTime(transcript);
        };
        session.intents.add(intent);
        if (intent == GuidedIntent.UNCLEAR) {
            if (session.fixedAttempt++ == 0) {
                session.guidedPhase = GuidedPhase.RETRY;
                playGuidedPrompt(session, switch (session.fixedPromptIndex) {
                    case 0 -> "identity-retry";
                    case 1 -> "visit-retry";
                    default -> "time-retry";
                });
            } else {
                session.manualFollowUp = true;
                closeFixedDialog(session, "manual-closing");
            }
            return;
        }
        if (session.fixedPromptIndex == 0 && intent == GuidedIntent.DENIED) {
            closeFixedDialog(session, "not-self-closing");
        } else if (session.fixedPromptIndex == 1 && intent == GuidedIntent.DENIED) {
            session.manualFollowUp = true;
            closeFixedDialog(session, "visit-declined");
        } else if (session.fixedPromptIndex >= fixedPrompts.size() - 2) {
            closeFixedDialog(session, fixedPrompts.get(fixedPrompts.size() - 1));
        } else {
            session.guidedPhase = GuidedPhase.RESPONSE;
            playGuidedPrompt(session, session.fixedPromptIndex == 0 ? "identity-confirmed" : "visit-accepted");
        }
    }

    private void closeFixedDialog(Session session, String prompt) {
        session.guidedPhase = GuidedPhase.CLOSING;
        playGuidedPrompt(session, prompt);
    }

    private void playGuidedPrompt(Session session, String prompt) {
        session.expectedType = TelephonyEventType.PLAYBACK_FINISHED;
        session.expectedOperation = telephony.play(session.taskId, session.channelId, prompt);
    }

    private void completeFixedDialog(Session session) {
        String transcript = String.join("\n", session.transcripts);
        String analysis = "{\"outcome\":\"COMPLETED\",\"identity\":\"" + intentAt(session, 0)
            + "\",\"visitConsent\":\"" + intentAt(session, 1) + "\",\"contactTime\":\""
            + escapeJson(lastTranscriptForStep(session, 2)) + "\",\"needManualFollowUp\":" + session.manualFollowUp + "}";
        tasks.completeGuidedDialog(session.taskId, transcript, analysis);
        session.terminal = true;
        active.remove(session.taskId);
        telephony.hangup(session.taskId, session.channelId);
    }

    private static String intentAt(Session session, int index) {
        return index < session.intents.size() ? session.intents.get(index).name() : "NOT_REACHED";
    }

    private static String lastTranscriptForStep(Session session, int step) {
        return step < session.transcripts.size() ? session.transcripts.get(session.transcripts.size() - 1) : "";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void beginTurn(Session session, DialogNode node, int attempt) {
        session.node = node;
        session.attempt = attempt;
        String prompt = node == DialogNode.ASK_IDENTITY ? "identity-question" : "payment-question";
        session.turnId = sessions.startTurn(session.taskId, node, attempt, prompt);
        session.expectedType = TelephonyEventType.PLAYBACK_FINISHED;
        session.expectedOperation = telephony.play(session.taskId, session.channelId, prompt);
    }

    private void playbackFinished(Session session, TelephonyEvent event) {
        if (!expected(session, event, TelephonyEventType.PLAYBACK_FINISHED)) return;
        if (session.node == DialogNode.CLOSING) {
            session.terminal = true;
            telephony.hangup(session.taskId, session.channelId);
            active.remove(session.taskId);
            return;
        }
        String prefix = session.node == DialogNode.ASK_IDENTITY ? "identity-answer" : "payment-answer";
        String recordingName = session.taskId + "-" + prefix + "-a" + session.attempt;
        session.expectedType = TelephonyEventType.RECORDING_FINISHED;
        session.expectedOperation = telephony.record(
            session.taskId, session.channelId, recordingName, 20, 3);
    }

    private void recordingFinished(Session session, TelephonyEvent event) {
        if (!expected(session, event, TelephonyEventType.RECORDING_FINISHED)) return;
        Path recording = event.recording() == null
            ? Path.of(event.operationId() + ".wav") : event.recording();
        String transcript = transcription.transcribe(recording);
        DialogDecision decision = decisions.decide(session.node, transcript);
        sessions.completeTurn(session.turnId, transcript, decision, recording);
        DialogTransition transition = stateMachine.decide(session.node, decision.intent(), session.attempt);
        if (transition.retry()) {
            beginTurn(session, session.node, session.attempt + 1);
        } else if (!transition.terminal()) {
            beginTurn(session, transition.nextNode(), 0);
        } else {
            session.node = DialogNode.CLOSING;
            session.expectedType = TelephonyEventType.PLAYBACK_FINISHED;
            session.expectedOperation = telephony.play(
                session.taskId, session.channelId, "closing");
        }
    }

    private boolean expected(Session session, TelephonyEvent event, TelephonyEventType type) {
        return session.expectedType == type
            && session.expectedOperation != null
            && session.expectedOperation.equals(event.operationId());
    }

    private void abort(Session session, String reason) {
        session.terminal = true;
        active.remove(session.taskId);
        try {
            tasks.transition(session.taskId, CallStatus.FAILED, failureSummary(reason));
        } catch (RuntimeException error) {
            log.warn("Dialog failure status update failed taskId={} reason={}",
                session.taskId, error.getClass().getSimpleName());
        }
        if (session.channelId == null) return;
        try {
            telephony.hangup(session.taskId, session.channelId);
        } catch (RuntimeException error) {
            log.warn("Dialog hangup failed taskId={} reason={}",
                session.taskId, error.getClass().getSimpleName());
        }
    }

    private static String failureSummary(String reason) {
        String summary = reason == null || reason.isBlank() ? "Dialog operation failed" : reason;
        return summary.length() <= 256 ? summary : summary.substring(0, 256);
    }

    private void finish(Session session) {
        session.terminal = true;
        active.remove(session.taskId);
    }

    private static final class Session {
        final UUID taskId;
        String channelId;
        DialogNode node;
        int attempt;
        UUID turnId;
        TelephonyEventType expectedType;
        String expectedOperation;
        boolean terminal;
        int fixedPromptIndex;
        int fixedAttempt;
        GuidedPhase guidedPhase;
        Path pendingRecording;
        final List<String> transcripts = new ArrayList<>();
        final List<GuidedIntent> intents = new ArrayList<>();
        boolean manualFollowUp;

        Session(UUID taskId) { this.taskId = taskId; }
    }

    private enum GuidedPhase { QUESTION, PROCESSING, RESPONSE, RETRY, CLOSING }
}
