package com.company.outbound.telephony;

import java.util.function.Consumer;

public interface TelephonyPort {
    String originate(CallCommand command);

    default void setEventListener(Consumer<TelephonyEvent> listener) {
    }
}
