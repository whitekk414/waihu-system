package com.company.outbound.telephony;

import java.util.UUID;

public record CallCommand(UUID taskId, String extension, String promptId) {
}
