package org.pulitko.aiprocessingservice.exception;

public class PromptNotFoundException extends BaseBusinessException {
    private final String ref;
    private static final String key = "PROMPT_NOT_FOUND";
    private static final int ERROR_CODE = 404;
    public PromptNotFoundException(String ref) {
        super(ref, ERROR_CODE);
        this.ref = ref;
    }

    @Override
    public String getKey() {
        return key;
    }
}
