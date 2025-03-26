/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.StringJoiner;

public class FormResponse {

  @Schema(description = "The unique identifier of the embedded form within one process.")
  private String id;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of `Form.processDefinitionId` field).")
  private String processDefinitionKey;

  @Schema(description = "The title of the form.")
  private String title;

  @Schema(description = "The form content.")
  private String schema;

  @Schema(
      description =
          "The version field is null in the case of an embedded form, while it represents the deployed form's version in other scenarios.",
      format = "int64")
  private Long version;

  @Schema(description = "The tenant ID associated with the form.")
  private String tenantId;

  @Schema(
      description =
          "Indicates whether the deployed form is deleted or not on Zeebe. This field is false by default, in the case of an embedded form.")
  private Boolean isDeleted;

  public String getId() {
    return id;
  }

  public FormResponse setId(final String id) {
    this.id = id;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public FormResponse setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getTitle() {
    return title;
  }

  public FormResponse setTitle(final String processName) {
    title = processName;
    return this;
  }

  public String getSchema() {
    return schema;
  }

  public FormResponse setSchema(final String schema) {
    this.schema = schema;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FormResponse setVersion(final Long version) {
    this.version = version;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FormResponse setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public FormResponse setIsDeleted(final Boolean isDeleted) {
    this.isDeleted = isDeleted;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, processDefinitionKey, title, schema, version, tenantId, isDeleted);
  }

  @Override
  public boolean equals(final Object o) {
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

  public static FormResponse fromFormEntity(final FormEntity form) {
    return new FormResponse()
        .setId(form.getFormId())
        .setProcessDefinitionKey(form.getProcessDefinitionId())
        .setSchema(form.getSchema())
        .setVersion(form.getVersion())
        .setTenantId(form.getTenantId())
        .setIsDeleted(form.getIsDeleted());
  }

  public static FormResponse fromFormEntity(final FormEntity form, final ProcessEntity processEntity) {
    return new FormResponse()
        .setId(form.getFormId())
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
