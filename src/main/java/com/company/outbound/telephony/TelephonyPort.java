package com.company.outbound.telephony;

import java.util.function.Consumer;
import java.util.UUID;

public interface TelephonyPort {
    String originate(CallCommand command);

    default String play(UUID taskId, String channelId, String promptId) {
        throw new UnsupportedOperationException("Playback is not supported");
    }

    default String record(UUID taskId, String channelId, String recordingName,
                          int maxDurationSeconds, int maxSilenceSeconds) {
        throw new UnsupportedOperationException("Recording is not supported");
    }

    default void hangup(UUID taskId, String channelId) {
        throw new UnsupportedOperationException("Hangup is not supported");
    }

    default void setEventListener(Consumer<TelephonyEvent> listener) {
    }
}
