/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import java.util.Objects;
import java.util.StringJoiner;

public class FormResponse {
  private String id;
  private String processDefinitionKey;
  private String title;
  private String schema;
  private Long version;
  private String tenantId;
  private Boolean isDeleted;

  public String getId() {
    return id;
  }

  public FormResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FormResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getTitle() {
    return title;
  }

  public FormResponse setTitle(String processName) {
    this.title = processName;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormResponse setSchema(String schema) {
    this.schema = schema;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormResponse setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FormResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormResponse setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
    return this;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", FormResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("title='" + title + "'")
        .add("schema='" + schema + "'")
        .add("version='" + version + "'")
        .add("tenantId='" + tenantId + "'")
        .add("isDeleted='" + isDeleted + "'")
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormResponse that = (FormResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(title, that.title)
        && Objects.equals(schema, that.schema)
        && Objects.equals(version, that.version)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(isDeleted, that.isDeleted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionKey, title, schema, version, tenantId, isDeleted);
  }

  public static FormResponse fromFormEntity(FormEntity form) {
    return new FormResponse()
        .setId(form.getBpmnId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setSchema(form.getSchema())
        .setVersion(form.getVersion())
        .setTenantId(form.getTenantId())
        .setIsDeleted(form.getIsDeleted());
  }

  public static FormResponse fromFormEntity(FormEntity form, ProcessEntity processEntity) {
    return new FormResponse()
        .setId(form.getBpmnId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setTitle(
            processEntity.getName() != null
                ? processEntity.getName()
                : processEntity.getBpmnProcessId())
        .setSchema(form.getSchema())
        .setVersion(form.getVersion())
        .setTenantId(form.getTenantId())
        .setIsDeleted(form.getIsDeleted());
  }
}
