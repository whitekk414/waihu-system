package com.company.outbound.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CallTaskRepository extends JpaRepository<CallTask, UUID> {
}
