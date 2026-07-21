package com.company.outbound.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
class TaskEventController {
    private final TaskEventService service;

    TaskEventController(TaskEventService service) {
        this.service = service;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return service.subscribe();
    }

    @GetMapping("/tasks/{taskId}")
    List<TaskEventView> history(@PathVariable UUID taskId) {
        return service.history(taskId);
    }
}
