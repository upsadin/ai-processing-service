package org.pulitko.aiprocessingservice.ai.client;

import org.pulitko.aiprocessingservice.ai.AiClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockAiClient implements AiClient {

    @Override
    public String analyze(String systemInstruction, String userContent) {
        return null;
    }
}
