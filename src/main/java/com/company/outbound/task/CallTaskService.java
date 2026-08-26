package com.company.outbound.task;

import com.company.outbound.events.TaskEventService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CallTaskService {
    private final CallTaskRepository repository;
    private final TaskEventService eventService;
    private final CallStateMachine stateMachine = new CallStateMachine();

    public CallTaskService(CallTaskRepository repository, TaskEventService eventService) {
        this.repository = repository;
        this.eventService = eventService;
    }

    @Transactional
    public CallTaskView create(CreateCallRequest request) {
        CallTask saved = repository.save(new CallTask(request.extension(), request.promptId()));
        eventService.publish(saved.getId(), saved.getStatus(), "任务已创建");
        return CallTaskView.from(saved);
    }

    @Transactional(readOnly = true)
    public CallTaskView get(UUID id) {
        return CallTaskView.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<CallTaskView> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
            .stream().map(CallTaskView::from).toList();
    }

    @Transactional
    public CallTaskView transition(UUID id, CallStatus next, String message) {
        CallTask task = find(id);
        if (!stateMachine.canMove(task.getStatus(), next)) {
            throw new IllegalStateException("Illegal transition: " + task.getStatus() + " -> " + next);
        }
        task.moveTo(next);
        eventService.publish(id, next, message);
        return CallTaskView.from(task);
    }

    @Transactional
    public void bindChannel(UUID id, String channelId) {
        find(id).bindAriChannel(channelId);
    }

    @Transactional
    public void saveProcessingResults(UUID id, String transcript, String analysisJson) {
        find(id).saveProcessingResults(transcript, analysisJson);
    }

    @Transactional
    public void completeGuidedDialog(UUID id, String transcript, String analysisJson) {
        CallTask task = find(id);
        task.saveProcessingResults(transcript, analysisJson);
        task.moveTo(CallStatus.COMPLETED);
        eventService.publish(id, CallStatus.COMPLETED, "Guided dialog completed");
    }

    private CallTask find(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }
}
