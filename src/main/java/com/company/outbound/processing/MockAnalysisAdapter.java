package com.company.outbound.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "outbound.processing.mode", havingValue = "mock", matchIfMissing = true)
class MockAnalysisAdapter implements AnalysisPort {
    @Override
    public AnalysisResult analyze(String transcript) {
        return new AnalysisResult(
            "客户已收到提醒，但未给出明确处理日期。",
            "本人接听",
            "中",
            false,
            null,
            false,
            List.of(),
            true,
            "由人工核实身份和具体处理安排"
        );
    }
}
