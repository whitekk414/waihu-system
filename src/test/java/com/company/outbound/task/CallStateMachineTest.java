package com.company.outbound.task;

import org.junit.jupiter.api.Test;

import static com.company.outbound.task.CallStatus.COMPLETED;
import static com.company.outbound.task.CallStatus.PENDING;
import static com.company.outbound.task.CallStatus.VALIDATING;
import static org.assertj.core.api.Assertions.assertThat;

class CallStateMachineTest {
    private final CallStateMachine machine = new CallStateMachine();

    @Test
    void allowsExpectedTransition() {
        assertThat(machine.canMove(PENDING, VALIDATING)).isTrue();
    }

    @Test
    void rejectsSkippingDirectlyToCompleted() {
        assertThat(machine.canMove(PENDING, COMPLETED)).isFalse();
    }
}
