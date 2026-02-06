package org.pulitko.aiprocessingservice.model;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder(setterPrefix = "with")
@Table("ai_prompt")
public class PromptEntity {

    @Id
    private Long id;
    private String ref;
    @Column("prompt_template")
    private String promptTemplate;
    @Column("schema_json")
    private String schemaJson;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
