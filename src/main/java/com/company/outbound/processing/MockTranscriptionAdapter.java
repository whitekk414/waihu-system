package com.company.outbound.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "outbound.processing.mode", havingValue = "mock", matchIfMissing = true)
class MockTranscriptionAdapter implements TranscriptionPort {
    @Override
    public String transcribe(Path recording) {
        return "测试客户表示已收到提醒，需要人工稍后核实具体处理时间。";
    }
}
