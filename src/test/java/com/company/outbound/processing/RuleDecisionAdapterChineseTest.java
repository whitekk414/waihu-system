package com.company.outbound.processing;

import com.company.outbound.dialog.DialogIntent;
import com.company.outbound.dialog.DialogNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDecisionAdapterChineseTest {

    private final RuleDecisionAdapter adapter = new RuleDecisionAdapter();

    @Test
    void recognizesChineseIdentityConfirmation() {
        assertThat(adapter.decide(DialogNode.ASK_IDENTITY, "是本人，我就是本人").intent())
            .isEqualTo(DialogIntent.SELF_CONFIRMED);
    }

    @Test
    void recognizesChinesePaymentPlan() {
        assertThat(adapter.decide(DialogNode.ASK_PAYMENT_PLAN, "我计划月底还款").intent())
            .isEqualTo(DialogIntent.HAS_PAYMENT_PLAN);
    }
}
