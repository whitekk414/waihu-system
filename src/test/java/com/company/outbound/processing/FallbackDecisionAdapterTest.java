package com.company.outbound.processing;

import com.company.outbound.dialog.DialogDecision;
import com.company.outbound.dialog.DialogIntent;
import com.company.outbound.dialog.DialogNode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackDecisionAdapterTest {

    @Test
    void returnsPrimaryDecisionWithoutCallingFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        DialogDecision primaryResult = decision(DialogIntent.SELF_CONFIRMED);
        DecisionPort primary = (node, transcript) -> primaryResult;
        DecisionPort fallback = (node, transcript) -> {
            fallbackCalls.incrementAndGet();
            return decision(DialogIntent.UNCLEAR);
        };

        DialogDecision result = new FallbackDecisionAdapter(primary, fallback)
            .decide(DialogNode.ASK_IDENTITY, "是本人");

        assertThat(result).isSameAs(primaryResult);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void invokesFallbackExactlyOnceWhenPrimaryFails() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        DecisionPort primary = (node, transcript) -> {
            throw new IllegalStateException("timeout");
        };
        DialogDecision fallbackResult = decision(DialogIntent.SELF_CONFIRMED);
        DecisionPort fallback = (node, transcript) -> {
            fallbackCalls.incrementAndGet();
            return fallbackResult;
        };

        DialogDecision result = new FallbackDecisionAdapter(primary, fallback)
            .decide(DialogNode.ASK_IDENTITY, "是本人");

        assertThat(result).isSameAs(fallbackResult);
        assertThat(fallbackCalls).hasValue(1);
    }

    private static DialogDecision decision(DialogIntent intent) {
        return new DialogDecision(intent, 0.9, DialogNode.ASK_PAYMENT_PLAN, false, "test");
    }
}
