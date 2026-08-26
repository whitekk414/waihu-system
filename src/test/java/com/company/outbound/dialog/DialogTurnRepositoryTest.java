package com.company.outbound.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DialogTurnRepositoryTest {
    @Autowired DialogTurnRepository repository;

    @Test
    void storesOrderedTurnsAndRejectsDuplicateNodeAttempt() {
        UUID taskId = UUID.randomUUID();
        repository.saveAndFlush(new DialogTurn(taskId, DialogNode.ASK_IDENTITY, 0, "identity"));
        repository.saveAndFlush(new DialogTurn(taskId, DialogNode.ASK_PAYMENT_PLAN, 0, "payment"));

        assertThat(repository.findByTaskIdOrderByCreatedAtAsc(taskId))
            .extracting(DialogTurn::getNode)
            .containsExactly(DialogNode.ASK_IDENTITY, DialogNode.ASK_PAYMENT_PLAN);

        assertThatThrownBy(() -> repository.saveAndFlush(
            new DialogTurn(taskId, DialogNode.ASK_IDENTITY, 0, "identity-again")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
