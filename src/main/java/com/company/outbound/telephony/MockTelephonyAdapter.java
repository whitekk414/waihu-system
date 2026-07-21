package com.company.outbound.telephony;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.company.outbound.task.CallStatus;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "outbound.telephony.mode", havingValue = "mock", matchIfMissing = true)
class MockTelephonyAdapter implements TelephonyPort {
    private Consumer<TelephonyEvent> listener = ignored -> { };

    @Override
    public String originate(CallCommand command) {
        emit(command, CallStatus.RINGING, "测试分机振铃");
        emit(command, CallStatus.ANSWERED, "测试分机已接听");
        emit(command, CallStatus.PLAYING_PROMPT, "正在播放固定话术");
        emit(command, CallStatus.WAITING_RESPONSE, "等待测试回答");
        emit(command, CallStatus.CALL_ENDED, "测试通话结束");
        emit(command, CallStatus.RECORDING_READY, "模拟录音已生成");
        return "mock-" + command.taskId();
    }

    @Override
    public void setEventListener(Consumer<TelephonyEvent> listener) {
        this.listener = listener;
    }

    private void emit(CallCommand command, CallStatus status, String message) {
        listener.accept(new TelephonyEvent(UUID.randomUUID(), command.taskId(), status, message, null));
    }
}
