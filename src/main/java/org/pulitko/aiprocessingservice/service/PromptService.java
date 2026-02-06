package org.pulitko.aiprocessingservice.service;


import org.pulitko.aiprocessingservice.model.Prompt;

public interface PromptService {

    public Prompt getByRef(String ref);

}
