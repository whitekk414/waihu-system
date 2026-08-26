package com.company.outbound.telephony;

import com.company.outbound.task.CallStatus;
import com.company.outbound.task.CallTaskService;
import com.company.outbound.task.CallTaskView;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "outbound.dialog.enabled", havingValue = "false", matchIfMissing = true)
public class CallOrchestrator {
    private final CallTaskService taskService;
    private final TelephonyPort telephony;
    private final Set<UUID> handledEvents = ConcurrentHashMap.newKeySet();

    public CallOrchestrator(CallTaskService taskService, TelephonyPort telephony) {
        this.taskService = taskService;
        this.telephony = telephony;
        this.telephony.setEventListener(this::onEvent);
    }

    public void start(UUID taskId) {
        CallTaskView task = taskService.get(taskId);
        taskService.transition(taskId, CallStatus.VALIDATING, "正在检查电话环境");
        taskService.transition(taskId, CallStatus.ORIGINATING, "正在发起呼叫");
        String channelId = telephony.originate(new CallCommand(taskId, task.extension(), task.promptId()));
        taskService.bindChannel(taskId, channelId);
    }

    private void onEvent(TelephonyEvent event) {
        if (!handledEvents.add(event.eventId())) {
            return;
        }
        taskService.transition(event.taskId(), event.status(), event.message());
    }

}
