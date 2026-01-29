package org.pulitko.aiprocessingservice.service;


import org.pulitko.aiprocessingservice.prompt.Prompt;

public interface PromptService {

    public Prompt getByRef(String ref);

}
