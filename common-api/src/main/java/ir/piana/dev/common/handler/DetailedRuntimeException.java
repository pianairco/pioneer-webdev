package ir.piana.dev.common.handler;

public class DetailedRuntimeException extends RuntimeException {
    private HandlerDetailedError detailedError;

    public DetailedRuntimeException(
            HandlerDetailedError detailedError) {
        super(detailedError.getErrorMessageKey());
        this.detailedError = detailedError;
    }

    public DetailedRuntimeException(
            HandlerDetailedError detailedError,
            Throwable throwable) {
        super(detailedError.getErrorMessageKey(), throwable);
        this.detailedError = detailedError;
    }

    public HandlerDetailedError getDetailedError() {
        return detailedError;
    }

    HandlerRuntimeException toHandlerRuntimeException(HandlerContext handlerContext) {
        return new HandlerRuntimeException(handlerContext, detailedError, getCause());
    }
}
