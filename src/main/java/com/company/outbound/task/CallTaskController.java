package com.company.outbound.task;

import com.company.outbound.telephony.CallOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
class CallTaskController {
    private final CallTaskService service;
    private final CallOrchestrator callOrchestrator;

    CallTaskController(CallTaskService service, CallOrchestrator callOrchestrator) {
        this.service = service;
        this.callOrchestrator = callOrchestrator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CallTaskView create(@Valid @RequestBody CreateCallRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<CallTaskView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    CallTaskView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void start(@PathVariable UUID id) {
        callOrchestrator.start(id);
    }
}
