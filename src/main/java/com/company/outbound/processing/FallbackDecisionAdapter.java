package com.company.outbound.processing;

import com.company.outbound.dialog.DialogDecision;
import com.company.outbound.dialog.DialogNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "outbound.processing.decision", havingValue = "gpt")
class FallbackDecisionAdapter implements DecisionPort {
    private static final Logger log = LoggerFactory.getLogger(FallbackDecisionAdapter.class);

    private final DecisionPort primary;
    private final DecisionPort fallback;

    @Autowired
    FallbackDecisionAdapter(GptDecisionAdapter primary, RuleDecisionAdapter fallback) {
        this((DecisionPort) primary, fallback);
    }

    FallbackDecisionAdapter(DecisionPort primary, DecisionPort fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public DialogDecision decide(DialogNode node, String transcript) {
        long started = System.nanoTime();
        try {
            DialogDecision decision = primary.decide(node, transcript);
            log.info("GPT decision completed node={} elapsedMs={}", node, elapsedMillis(started));
            return decision;
        } catch (RuntimeException error) {
            log.warn("GPT decision degraded to local rules node={} elapsedMs={} reason={}",
                node, elapsedMillis(started), error.getClass().getSimpleName());
            return fallback.decide(node, transcript);
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
