package com.company.outbound.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialogStateMachineTest {
    private final DialogStateMachine machine = new DialogStateMachine();

    @Test
    void confirmedIdentityAdvancesToPaymentQuestion() {
        var result = machine.decide(DialogNode.ASK_IDENTITY, DialogIntent.SELF_CONFIRMED, 0);
        assertThat(result.nextNode()).isEqualTo(DialogNode.ASK_PAYMENT_PLAN);
        assertThat(result.terminal()).isFalse();
    }

    @Test
    void notSelfTerminates() {
        var result = machine.decide(DialogNode.ASK_IDENTITY, DialogIntent.NOT_SELF, 0);
        assertThat(result.terminalOutcome()).isEqualTo("NOT_SELF");
        assertThat(result.terminal()).isTrue();
    }

    @Test
    void unclearRetriesOnceThenHandsToHuman() {
        assertThat(machine.decide(DialogNode.ASK_IDENTITY, DialogIntent.UNCLEAR, 0).retry()).isTrue();
        assertThat(machine.decide(DialogNode.ASK_IDENTITY, DialogIntent.UNCLEAR, 1).needHuman()).isTrue();
    }

    @Test
    void allPaymentOutcomesTerminate() {
        for (var intent : new DialogIntent[]{DialogIntent.HAS_PAYMENT_PLAN,
            DialogIntent.NO_PAYMENT_PLAN, DialogIntent.REFUSE_TO_ANSWER}) {
            var result = machine.decide(DialogNode.ASK_PAYMENT_PLAN, intent, 0);
            assertThat(result.terminal()).isTrue();
            assertThat(result.terminalOutcome()).isEqualTo(intent.name());
        }
    }

    @Test
    void rejectsIntentInvalidForCurrentNode() {
        assertThatThrownBy(() -> machine.decide(
            DialogNode.ASK_IDENTITY, DialogIntent.HAS_PAYMENT_PLAN, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
