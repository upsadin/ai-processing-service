package org.pulitko.aiprocessingservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder(setterPrefix = "with")
@Table("ai_prompt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromptEntity {

    @Id
    private Long id;
    private String ref;
    @Column("prompt_template")
    private String promptTemplate;
    @Column("schema_json")
    private String schemaJson;
    private boolean active;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
