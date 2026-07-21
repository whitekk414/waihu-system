package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;

import java.nio.file.Path;
import java.util.UUID;

public record TelephonyEvent(
    UUID eventId,
    UUID taskId,
    CallStatus status,
    String message,
    Path recording
) {
}
