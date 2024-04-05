/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;

import java.util.Objects;

public class FormEntity extends TenantAwareTasklistEntity<FormEntity> {

  private String bpmnId;
  private String processDefinitionId;
  private String schema;
  private Long version;
  private String tenantId;
  private Boolean embedded;
  private Boolean isDeleted;

  public FormEntity() {}

  /* This constructor is used for either embedded or linked forms. */
  public FormEntity(
      String processDefinitionId,
      String bpmnId,
      String schema,
      Long version,
      String tenantId,
      String formKey,
      Boolean embedded,
      Boolean isDeleted) {
    if (embedded) {
      setId(createId(processDefinitionId, bpmnId));
    } else {
      setId(formKey);
    }
    this.bpmnId = bpmnId;
    this.processDefinitionId = processDefinitionId;
    this.schema = schema;
    this.version = version;
    this.tenantId = tenantId;
    this.embedded = embedded;
    this.isDeleted = isDeleted;
  }

  /* This constructor is used for embedded forms. */

  public FormEntity(String processDefinitionId, String bpmnId, String schema) {
    this(processDefinitionId, bpmnId, schema, DEFAULT_TENANT_IDENTIFIER);
  }

  public FormEntity(String processDefinitionId, String bpmnId, String schema, String tenantId) {
    setId(createId(processDefinitionId, bpmnId));
    this.setTenantId(tenantId);
    this.bpmnId = bpmnId;
    this.processDefinitionId = processDefinitionId;
    this.schema = schema;
    this.tenantId = tenantId;
    this.embedded = true;
    this.isDeleted = false;
  }

  public Boolean getEmbedded() {
    return embedded;
  }

  public FormEntity setEmbedded(Boolean embedded) {
    this.embedded = embedded;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormEntity setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public String getBpmnId() {
    return bpmnId;
  }

  public FormEntity setBpmnId(final String bpmnId) {
    this.bpmnId = bpmnId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public FormEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormEntity setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FormEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormEntity setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
    return this;
  }

  public static String createId(String processId, String formKey) {
    return String.format("%s_%s", processId, formKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnId, processDefinitionId, schema, version);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FormEntity that = (FormEntity) o;
    return Objects.equals(bpmnId, that.bpmnId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(schema, that.schema)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(embedded, that.embedded)
        && Objects.equals(isDeleted, that.isDeleted)
        && Objects.equals(version, that.version);
  }

  @Override
  public String toString() {
    return "FormEntity{"
        + "formKey='"
        + bpmnId
        + '\''
        + ", processId='"
        + processDefinitionId
        + '\''
        + ", schema='"
        + schema
        + '\''
        + ", version='"
        + version
        + '\''
        + ", isDeleted="
        + isDeleted
        + '}';
  }
}
