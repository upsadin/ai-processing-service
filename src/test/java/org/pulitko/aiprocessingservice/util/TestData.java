package org.pulitko.aiprocessingservice.util;

import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.prompt.Prompt;
import org.pulitko.aiprocessingservice.prompt.PromptEntity;

public class TestData {
    public static final String SOURCE_ID_JAVACANDIDATE = "src-1";

    public static final String REF_JAVACANDIDATE = "candidate_java";
    public static final String TYPE_JAVACANDIDATE = "txt";
    public static final String PAYLOAD_JAVACANDIDATE = "java candidate CV, 2 years experience, knows Postgres";
    public static final IncomingMessage INCOMING_MESSAGE = new IncomingMessage(
            TYPE_JAVACANDIDATE, REF_JAVACANDIDATE, PAYLOAD_JAVACANDIDATE
    );
    public static final IncomingMessage INCOMING_MESSAGE_WITH_NULL_REF = new IncomingMessage(
            TYPE_JAVACANDIDATE, null, PAYLOAD_JAVACANDIDATE
    );
    public static final IncomingMessage INCOMING_MESSAGE_WITH_NULL_PAYLOAD = new IncomingMessage(
            TYPE_JAVACANDIDATE, REF_JAVACANDIDATE, null
    );
    public static final IncomingMessage INCOMING_MESSAGE_WITH_NULL_TYPE = new IncomingMessage(
            null, REF_JAVACANDIDATE, PAYLOAD_JAVACANDIDATE
    );

    public static final String TEMPLATE_JAVACANDIDATE = "Promt for java candidate";
    public static final String SCHEMA_JAVACANDIDATE = "java candidate CV";

    public static final Prompt PROMPT = new Prompt(REF_JAVACANDIDATE, TEMPLATE_JAVACANDIDATE, SCHEMA_JAVACANDIDATE);
    public static final PromptEntity PROMPT_ENTITY = PromptEntity.builder()
            .withId(1L)
            .withRef(REF_JAVACANDIDATE)
            .withPromptTemplate(TEMPLATE_JAVACANDIDATE)
            .withSchemaJson(SCHEMA_JAVACANDIDATE)
            .withActive(true)
            .build();

    public static final String SUCCESS_AI_RESULT =
            """
                    {
                      "matches": true,
                      "confidence": 0.95,
                      "reason": "Кандидат соответствует всем критериям: имеет коммерческий опыт на Java от 2 лет и опыт работы с PostgreSQL.",
                      "full_name": "Иван Иванов",
                      "contacts": {
                        "email": "еуые@mail.ru",
                        "phone": "+77777777777",
                        "linkedin": "https://www.linkedin.com/in/test",
                        "telegram": "https://t.me/test"
                      }
                    }
                            """;
}
