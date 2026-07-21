package com.company.outbound.events;

import com.company.outbound.task.CallStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskEventService {
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    private final TaskEventRepository repository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public TaskEventService(TaskEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskEventView publish(UUID taskId, CallStatus status, String message) {
        TaskEventView view = TaskEventView.from(repository.save(new TaskEvent(taskId, status, message)));
        emitters.removeIf(emitter -> !send(emitter, "task-event", view));
        return view;
    }

    @Transactional(readOnly = true)
    public List<TaskEventView> history(UUID taskId) {
        return repository.findByTaskIdOrderByOccurredAtAsc(taskId)
            .stream().map(TaskEventView::from).toList();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, "ready", java.util.Map.of("status", "connected"));
        return emitter;
    }

    private boolean send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            return true;
        } catch (IOException | IllegalStateException error) {
            emitter.complete();
            return false;
        }
    }
}
