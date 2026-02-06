package org.pulitko.aiprocessingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class AiProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiProcessingServiceApplication.class, args);
    }

}
