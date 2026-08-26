package com.company.outbound.processing;

import com.company.outbound.dialog.DialogDecision;
import com.company.outbound.dialog.DialogNode;

public interface DecisionPort {
    DialogDecision decide(DialogNode node, String transcript);
}
