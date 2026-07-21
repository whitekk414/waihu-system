package com.company.outbound.processing;

import java.time.LocalDate;
import java.util.List;

public record AnalysisResult(
    String summary,
    String contactResult,
    String willingness,
    boolean promiseToPay,
    LocalDate promisedDate,
    boolean dispute,
    List<String> riskFlags,
    boolean needsHumanReview,
    String nextAction
) {
}
