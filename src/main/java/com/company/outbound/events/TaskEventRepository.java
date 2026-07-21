package com.company.outbound.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskEventRepository extends JpaRepository<TaskEvent, UUID> {
    List<TaskEvent> findByTaskIdOrderByOccurredAtAsc(UUID taskId);
}
