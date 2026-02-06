package org.pulitko.aiprocessingservice.exception;

public abstract class BaseBusinessException extends RuntimeException {
    private String sourceId;
    private final int errorCode;

    protected BaseBusinessException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseBusinessException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getSourceId() {
        return sourceId;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public BaseBusinessException withSourceId(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public abstract String getKey();
}