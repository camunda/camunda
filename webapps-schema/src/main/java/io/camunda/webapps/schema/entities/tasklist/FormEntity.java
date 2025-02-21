/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class FormEntity implements ExporterEntity<FormEntity>, TenantOwned {

  private String id;
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  private long key;

  @JsonProperty("bpmnId")
  private String formId;

  private String schema;
  private Long version;
  private Boolean isDeleted;
  private String processDefinitionId;
  private boolean embedded;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public FormEntity setId(final String id) {
    this.id = id;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public FormEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public FormEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public FormEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormEntity setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormEntity setVersion(final Long version) {
    this.version = version;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormEntity setIsDeleted(final Boolean deleted) {
    isDeleted = deleted;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public FormEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public boolean getEmbedded() {
    return embedded;
  }

  public FormEntity setEmbedded(final boolean embedded) {
    this.embedded = embedded;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, key, tenantId, formId, schema, version, isDeleted);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormEntity that = (FormEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(formId, that.formId)
        && Objects.equals(schema, that.schema)
        && Objects.equals(version, that.version)
        && Objects.equals(isDeleted, that.isDeleted);
  }

  @Override
  public String toString() {
    return "FormEntity{"
        + "id='"
        + id
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", key="
        + key
        + ", formId='"
        + formId
        + '\''
        + ", schema='"
        + schema
        + '\''
        + ", version="
        + version
        + ", isDeleted="
        + isDeleted
        + '}';
  }
}
