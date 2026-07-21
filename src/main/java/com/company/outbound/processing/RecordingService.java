package com.company.outbound.processing;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class RecordingService {
    public String validateAndHash(Path recording) {
        try {
            if (!Files.isRegularFile(recording) || !recording.getFileName().toString().toLowerCase().endsWith(".wav")) {
                throw new IllegalArgumentException("Recording must be a WAV file");
            }
            byte[] bytes = Files.readAllBytes(recording);
            if (bytes.length <= 44) {
                throw new IllegalArgumentException("Recording is empty or too short");
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot read recording", error);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
