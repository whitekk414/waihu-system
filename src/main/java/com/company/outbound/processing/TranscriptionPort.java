package com.company.outbound.processing;

import java.nio.file.Path;

public interface TranscriptionPort {
    String transcribe(Path recording);
}
