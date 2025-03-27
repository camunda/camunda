/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.tasklist.DraftTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.StringJoiner;

public class VariableResponse {

  @Schema(description = "The ID of the variable")
  private String id;

  @Schema(description = "The name of the variable")
  private String name;

  @Schema(description = "The full value of the variable")
  private String value;

  @Schema(description = "The draft value of the variable")
  private DraftVariableValue draft;

  @Schema(description = "The tenant ID associated with the variable")
  private String tenantId;

  public String getId() {
    return id;
  }

  public VariableResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableResponse setValue(String value) {
    this.value = value;
    return this;
  }

  public DraftVariableValue getDraft() {
    return draft;
  }

  public VariableResponse setDraft(DraftVariableValue draft) {
    this.draft = draft;
    return this;
  }

  public VariableResponse addDraft(DraftTaskVariableEntity draftTaskVariable) {
    this.draft =
        new VariableResponse.DraftVariableValue().setValue(draftTaskVariable.getFullValue());
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public static VariableResponse createFrom(VariableEntity variableEntity) {
    return new VariableResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(
            variableEntity.getIsPreview()
                ? variableEntity.getFullValue()
                : variableEntity.getValue())
        .setTenantId(variableEntity.getTenantId());
  }

  public static VariableResponse createFrom(DraftTaskVariableEntity draftTaskVariable) {
    return new VariableResponse()
        .setId(draftTaskVariable.getId())
        .setName(draftTaskVariable.getName())
        .setTenantId(draftTaskVariable.getTenantId())
        .setDraft(
            new VariableResponse.DraftVariableValue().setValue(draftTaskVariable.getFullValue()));
  }

  public static VariableResponse createFrom(SnapshotTaskVariableEntity variableEntity) {
    return new VariableResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(variableEntity.getFullValue())
        .setTenantId(variableEntity.getTenantId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableResponse that = (VariableResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(draft, that.draft)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, draft, tenantId);
  }

  @Override
  public String toString() {
    return "VariableResponse{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", draft="
        + draft
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }

  public static class DraftVariableValue {
    private String value;

    public String getValue() {
      return value;
    }

    public DraftVariableValue setValue(String value) {
      this.value = value;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final DraftVariableValue that = (DraftVariableValue) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", DraftVariableValue.class.getSimpleName() + "[", "]")
          .add("value='" + value + "'")
          .toString();
    }
  }
}
