package com.company.outbound.dialog;

public record DialogTransition(
    DialogNode nextNode,
    boolean retry,
    boolean needHuman,
    boolean terminal,
    String terminalOutcome
) {
    static DialogTransition advance(DialogNode nextNode) {
        return new DialogTransition(nextNode, false, false, false, null);
    }

    static DialogTransition retry(DialogNode node) {
        return new DialogTransition(node, true, false, false, null);
    }

    static DialogTransition human(DialogNode node) {
        return new DialogTransition(node, false, true, true, "NEED_HUMAN");
    }

    static DialogTransition terminal(String outcome) {
        return new DialogTransition(DialogNode.COMPLETED, false, false, true, outcome);
    }
}
