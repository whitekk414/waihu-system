package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;

import java.nio.file.Path;
import java.util.UUID;

public record TelephonyEvent(
    UUID eventId,
    UUID taskId,
    TelephonyEventType type,
    String channelId,
    String operationId,
    String message,
    Path recording
) {
    public TelephonyEvent(UUID eventId, UUID taskId, CallStatus status, String message, Path recording) {
        this(eventId, taskId, fromStatus(status), null, null, message, recording);
    }

    public CallStatus status() {
        return switch (type) {
            case CHANNEL_RINGING -> CallStatus.RINGING;
            case CHANNEL_ANSWERED -> CallStatus.ANSWERED;
            case PLAYBACK_FINISHED -> CallStatus.WAITING_RESPONSE;
            case RECORDING_FINISHED -> CallStatus.RECORDING_READY;
            case CHANNEL_ENDED -> CallStatus.CALL_ENDED;
            case OPERATION_FAILED -> CallStatus.FAILED;
        };
    }

    private static TelephonyEventType fromStatus(CallStatus status) {
        return switch (status) {
            case RINGING -> TelephonyEventType.CHANNEL_RINGING;
            case ANSWERED -> TelephonyEventType.CHANNEL_ANSWERED;
            case RECORDING_READY -> TelephonyEventType.RECORDING_FINISHED;
            case CALL_ENDED -> TelephonyEventType.CHANNEL_ENDED;
            case FAILED -> TelephonyEventType.OPERATION_FAILED;
            default -> TelephonyEventType.OPERATION_FAILED;
        };
    }
}
