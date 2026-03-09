package org.pulitko.aiprocessingservice.exception;

public class MissingHeaderException extends BaseBusinessException {

    private final String headerName;

    public MissingHeaderException(String headerName) {
        super("Missing required Kafka header: " + headerName, 4001);
        this.headerName = headerName;
    }

    @Override
    public String getKey() {
        return "MISSING_HEADER";
    }

    public String getHeaderName() {
        return headerName;
    }
}
