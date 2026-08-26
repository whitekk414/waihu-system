package com.company.outbound.dialog;

public final class DialogStateMachine {
    public DialogTransition decide(DialogNode node, DialogIntent intent, int attempt) {
        if (attempt < 0) throw new IllegalArgumentException("attempt must not be negative");
        return switch (node) {
            case ASK_IDENTITY -> decideIdentity(intent, attempt);
            case ASK_PAYMENT_PLAN -> decidePayment(intent, attempt);
            case CLOSING, COMPLETED -> throw new IllegalStateException("Node is not decidable: " + node);
        };
    }

    private DialogTransition decideIdentity(DialogIntent intent, int attempt) {
        return switch (intent) {
            case SELF_CONFIRMED -> DialogTransition.advance(DialogNode.ASK_PAYMENT_PLAN);
            case NOT_SELF -> DialogTransition.terminal("NOT_SELF");
            case UNCLEAR -> unclear(DialogNode.ASK_IDENTITY, attempt);
            default -> throw invalid(DialogNode.ASK_IDENTITY, intent);
        };
    }

    private DialogTransition decidePayment(DialogIntent intent, int attempt) {
        return switch (intent) {
            case HAS_PAYMENT_PLAN, NO_PAYMENT_PLAN, REFUSE_TO_ANSWER ->
                DialogTransition.terminal(intent.name());
            case UNCLEAR -> unclear(DialogNode.ASK_PAYMENT_PLAN, attempt);
            default -> throw invalid(DialogNode.ASK_PAYMENT_PLAN, intent);
        };
    }

    private DialogTransition unclear(DialogNode node, int attempt) {
        return attempt == 0 ? DialogTransition.retry(node) : DialogTransition.human(node);
    }

    private IllegalArgumentException invalid(DialogNode node, DialogIntent intent) {
        return new IllegalArgumentException("Intent " + intent + " is invalid for " + node);
    }
}
