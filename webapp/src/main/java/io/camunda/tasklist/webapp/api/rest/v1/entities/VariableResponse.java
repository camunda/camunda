/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import java.util.Objects;
import java.util.StringJoiner;

public class VariableResponse {

  private String id;
  private String name;
  private String value;

  private DraftVariableValue draft;

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

  @Override
  public String toString() {
    return new StringJoiner(", ", VariableResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("value='" + value + "'")
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
    final VariableResponse that = (VariableResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(draft, that.draft);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, draft);
  }

  public static VariableResponse createFrom(VariableEntity variableEntity) {
    return new VariableResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(variableEntity.getFullValue());
  }

  public static VariableResponse createFrom(DraftTaskVariableEntity draftTaskVariable) {
    return new VariableResponse()
        .setId(draftTaskVariable.getId())
        .setName(draftTaskVariable.getName())
        .setDraft(
            new VariableResponse.DraftVariableValue().setValue(draftTaskVariable.getFullValue()));
  }

  public static VariableResponse createFrom(TaskVariableEntity variableEntity) {
    return new VariableResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(variableEntity.getFullValue());
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
