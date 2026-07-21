package com.company.outbound.task;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CallStateMachine {
    private final Map<CallStatus, Set<CallStatus>> transitions = new EnumMap<>(CallStatus.class);

    public CallStateMachine() {
        allow(CallStatus.PENDING, CallStatus.VALIDATING);
        allow(CallStatus.VALIDATING, CallStatus.ORIGINATING);
        allow(CallStatus.ORIGINATING, CallStatus.RINGING);
        allow(CallStatus.RINGING, CallStatus.ANSWERED);
        allow(CallStatus.ANSWERED, CallStatus.PLAYING_PROMPT);
        allow(CallStatus.PLAYING_PROMPT, CallStatus.WAITING_RESPONSE);
        allow(CallStatus.WAITING_RESPONSE, CallStatus.CALL_ENDED);
        allow(CallStatus.CALL_ENDED, CallStatus.RECORDING_READY);
        allow(CallStatus.RECORDING_READY, CallStatus.TRANSCRIBING);
        allow(CallStatus.TRANSCRIBING, CallStatus.ANALYZING);
        allow(CallStatus.ANALYZING, CallStatus.COMPLETED);
        allow(CallStatus.FAILED, CallStatus.VALIDATING);
        for (CallStatus status : CallStatus.values()) {
            if (status != CallStatus.COMPLETED && status != CallStatus.FAILED) {
                allow(status, CallStatus.FAILED);
            }
        }
    }

    public boolean canMove(CallStatus from, CallStatus to) {
        return transitions.getOrDefault(from, Set.of()).contains(to);
    }

    private void allow(CallStatus from, CallStatus to) {
        transitions.computeIfAbsent(from, ignored -> EnumSet.noneOf(CallStatus.class)).add(to);
    }
}
