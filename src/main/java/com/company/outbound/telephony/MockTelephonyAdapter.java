package com.company.outbound.telephony;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "outbound.telephony.mode", havingValue = "mock", matchIfMissing = true)
class MockTelephonyAdapter implements TelephonyPort {
    private Consumer<TelephonyEvent> listener = ignored -> { };
    private final AtomicInteger operationSequence = new AtomicInteger();

    @Override
    public String originate(CallCommand command) {
        String channelId = "mock-" + command.taskId();
        emit(command.taskId(), TelephonyEventType.CHANNEL_ANSWERED, channelId, null,
            "模拟电话已接通", null);
        return channelId;
    }

    @Override
    public String play(UUID taskId, String channelId, String promptId) {
        String operationId = "mock-play-" + operationSequence.incrementAndGet();
        emit(taskId, TelephonyEventType.PLAYBACK_FINISHED, channelId, operationId,
            "模拟话术播放完成", null);
        return operationId;
    }

    @Override
    public String record(UUID taskId, String channelId, String recordingName,
                         int maxDurationSeconds, int maxSilenceSeconds) {
        emit(taskId, TelephonyEventType.RECORDING_FINISHED, channelId, recordingName,
            "模拟录音完成", Path.of(recordingName + ".wav"));
        return recordingName;
    }

    @Override
    public void hangup(UUID taskId, String channelId) {
        emit(taskId, TelephonyEventType.CHANNEL_ENDED, channelId, null,
            "模拟通话结束", null);
    }

    @Override
    public void setEventListener(Consumer<TelephonyEvent> listener) {
        this.listener = listener;
    }

    private void emit(UUID taskId, TelephonyEventType type, String channelId,
                      String operationId, String message, Path recording) {
        listener.accept(new TelephonyEvent(
            UUID.randomUUID(), taskId, type, channelId, operationId, message, recording));
    }
}
