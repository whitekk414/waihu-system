package com.company.outbound.processing;

public interface AnalysisPort {
    AnalysisResult analyze(String transcript);
}
