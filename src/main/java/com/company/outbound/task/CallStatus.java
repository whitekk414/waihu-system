package com.company.outbound.task;

public enum CallStatus {
    PENDING,
    VALIDATING,
    ORIGINATING,
    RINGING,
    ANSWERED,
    PLAYING_PROMPT,
    WAITING_RESPONSE,
    CALL_ENDED,
    RECORDING_READY,
    TRANSCRIBING,
    ANALYZING,
    COMPLETED,
    FAILED
}
