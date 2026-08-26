package com.company.outbound.processing;

import com.company.outbound.dialog.*;
import org.springframework.stereotype.Component;

@Component
class RuleDecisionAdapter implements DecisionPort {
    @Override
    public DialogDecision decide(DialogNode node, String transcript) {
        String text = transcript == null ? "" : transcript.replaceAll("\\s+", "");
        DialogIntent intent = switch (node) {
            case ASK_IDENTITY -> identity(text);
            case ASK_PAYMENT_PLAN -> payment(text);
            default -> throw new IllegalArgumentException("Node is not decidable: " + node);
        };
        DialogTransition transition = new DialogStateMachine().decide(node, intent, 0);
        return new DialogDecision(intent, intent == DialogIntent.UNCLEAR ? 0.3 : 0.9,
            transition.nextNode(), transition.needHuman(), text);
    }

    private DialogIntent identity(String text) {
        if (text.contains("不是") || text.contains("打错") || text.contains("不认识")) {
            return DialogIntent.NOT_SELF;
        }
        if (text.contains("本人") || text.contains("是我") || text.contains("我就是")) {
            return DialogIntent.SELF_CONFIRMED;
        }
        return DialogIntent.UNCLEAR;
    }

    private DialogIntent payment(String text) {
        if (text.contains("不回答") || text.contains("拒绝")) return DialogIntent.REFUSE_TO_ANSWER;
        if (text.contains("没有") || text.contains("没钱") || text.contains("不还")) {
            return DialogIntent.NO_PAYMENT_PLAN;
        }
        if (text.contains("还款") || text.contains("月底") || text.contains("明天") || text.contains("计划")) {
            return DialogIntent.HAS_PAYMENT_PLAN;
        }
        return DialogIntent.UNCLEAR;
    }
}
