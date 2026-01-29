package org.pulitko.aiprocessingservice.ai;

import org.springframework.stereotype.Component;

@Component
public class MockAiClient implements AiClient {

    @Override
    public String analyze(String prompt) {
        return null;
    }
}
