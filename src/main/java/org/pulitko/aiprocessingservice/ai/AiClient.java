package org.pulitko.aiprocessingservice.ai;

import org.springframework.stereotype.Component;

@Component
public interface AiClient {
    String analyze(String prompt);
}
