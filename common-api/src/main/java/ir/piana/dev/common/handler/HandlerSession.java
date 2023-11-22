package ir.piana.dev.common.handler;

import ir.piana.dev.common.util.SelfExpiringHashMap;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class HandlerSession {
    private ThreadLocal<SelfExpiringHashMap<String, Object>> handlerSessionThreadLocal = new ThreadLocal<>();

    void set(SelfExpiringHashMap<String, Object> selfExpiringHashMap) {
        this.handlerSessionThreadLocal.set(selfExpiringHashMap);
    }

    void remove() {
        this.handlerSessionThreadLocal.remove();
    }

    public SelfExpiringHashMap<String, Object> get() {
        return handlerSessionThreadLocal.get();
    }
}
