package org.pulitko.aiprocessingservice.ai;

public interface AiClient {
    String analyze(String systemInstruction, String userContent, String schemaJson, String ref);
}
