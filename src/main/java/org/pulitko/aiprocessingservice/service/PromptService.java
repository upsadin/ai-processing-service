package org.pulitko.aiprocessingservice.service;


import org.pulitko.aiprocessingservice.dto.Prompt;

public interface PromptService {

    public Prompt getActivePrompt(String ref);

}
