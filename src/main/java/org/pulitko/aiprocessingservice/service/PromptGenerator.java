package org.pulitko.aiprocessingservice.service;

import org.springframework.stereotype.Service;

@Service
public class PromptGenerator {

    public String generate(String template, String payload) {
        if (template == null) {
            return "";
        }
        return template.replace("{{payload}}", payload != null ? payload : "");
    }
}
