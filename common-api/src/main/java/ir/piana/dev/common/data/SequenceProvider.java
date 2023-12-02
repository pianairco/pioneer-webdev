package ir.piana.dev.common.data;

import java.util.concurrent.atomic.AtomicLong;

public abstract class SequenceProvider {
    private AtomicLong nextProvider;
    private AtomicLong afterFetch;

    private static final AtomicLong lock = new AtomicLong(0);

    public final long next() {
        if (afterFetch != null && (afterFetch.longValue() + fetchSize() - 1) > nextProvider.longValue()) {
            long l = nextProvider.incrementAndGet();
            if ((afterFetch.longValue() + fetchSize() - 1) > l)
                return l;
            else
                return maybeFetch();
        } else {
            return maybeFetch();
        }
    }

    private synchronized long maybeFetch() {
        if (afterFetch != null && (afterFetch.longValue() + fetchSize() - 1) > nextProvider.longValue()) {
            return nextProvider.incrementAndGet();
        } else {
            afterFetch = new AtomicLong(fetch());
        }
        nextProvider = new AtomicLong(afterFetch.intValue());
        return nextProvider.longValue();
    }

    protected abstract int fetchSize();
    protected abstract long fetch();
}
