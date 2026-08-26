package com.company.outbound.task;

import com.company.outbound.telephony.CallOrchestrator;
import com.company.outbound.dialog.DialogOrchestrator;
import com.company.outbound.dialog.DialogSessionService;
import com.company.outbound.dialog.DialogTurnView;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
class CallTaskController {
    private final CallTaskService service;
    private final ObjectProvider<CallOrchestrator> callOrchestrator;
    private final ObjectProvider<DialogOrchestrator> dialogOrchestrator;
    private final DialogSessionService dialogSessions;

    CallTaskController(CallTaskService service,
                       ObjectProvider<CallOrchestrator> callOrchestrator,
                       ObjectProvider<DialogOrchestrator> dialogOrchestrator,
                       DialogSessionService dialogSessions) {
        this.service = service;
        this.callOrchestrator = callOrchestrator;
        this.dialogOrchestrator = dialogOrchestrator;
        this.dialogSessions = dialogSessions;
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

    @GetMapping("/{id}/turns")
    List<DialogTurnView> turns(@PathVariable UUID id) {
        service.get(id);
        return dialogSessions.turns(id).stream().map(DialogTurnView::from).toList();
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void start(@PathVariable UUID id) {
        CallOrchestrator orchestrator = callOrchestrator.getIfAvailable();
        if (orchestrator == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy call mode is disabled");
        }
        orchestrator.start(id);
    }

    @PostMapping("/{id}/start-dialog")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void startDialog(@PathVariable UUID id, @RequestBody StartDialogRequest request) {
        if (request == null || !request.confirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit call confirmation is required");
        }
        CallTaskView task = service.get(id);
        if (!task.extension().matches("^1\\d{10}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A single valid mobile number is required");
        }
        DialogOrchestrator orchestrator = dialogOrchestrator.getIfAvailable();
        if (orchestrator == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dialog mode is not ready");
        }
        orchestrator.start(id, task.extension());
    }

    record StartDialogRequest(boolean confirmed) {}
}
