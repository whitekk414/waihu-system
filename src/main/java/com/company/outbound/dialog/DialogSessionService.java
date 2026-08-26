package com.company.outbound.dialog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DialogSessionService {
    private final DialogTurnRepository repository;

    public DialogSessionService(DialogTurnRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID startTurn(UUID taskId, DialogNode node, int attempt, String promptId) {
        return repository.save(new DialogTurn(taskId, node, attempt, promptId)).getId();
    }

    @Transactional
    public void completeTurn(UUID turnId, String transcript, DialogDecision decision, Path recording) {
        DialogTurn turn = repository.findById(turnId)
            .orElseThrow(() -> new IllegalArgumentException("Dialog turn not found"));
        turn.complete(recording == null ? null : recording.getFileName().toString(), transcript, decision);
    }

    @Transactional(readOnly = true)
    public List<DialogTurn> turns(UUID taskId) {
        return repository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }
}
