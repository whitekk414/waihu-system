package com.company.outbound.dialog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DialogTurnRepository extends JpaRepository<DialogTurn, UUID> {
    List<DialogTurn> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
