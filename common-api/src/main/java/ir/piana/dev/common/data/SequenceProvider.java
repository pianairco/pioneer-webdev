package ir.piana.dev.common.data;

import java.util.concurrent.atomic.AtomicLong;

public abstract class SequenceProvider {
    private AtomicLong nextProvider;
    private AtomicLong afterFetch;

    public long next() {
        if (afterFetch != null && (afterFetch.longValue() + fetchSize() - 1) > nextProvider.longValue()) {
            return nextProvider.incrementAndGet();
        } else {
            afterFetch = new AtomicLong(fetch());
            nextProvider = new AtomicLong(afterFetch.intValue());
            return nextProvider.longValue();
        }
    }

    protected abstract int fetchSize();
    protected abstract long fetch();
}
