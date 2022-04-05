/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VariableDTO {

  private String id;
  private String name;
  private String value;
  private boolean isValueTruncated;
  private String previewValue;

  public String getId() {
    return id;
  }

  public VariableDTO setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableDTO setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableDTO setValue(final String value) {
    this.value = value;
    return this;
  }

  public boolean getIsValueTruncated() {
    return isValueTruncated;
  }

  public VariableDTO setIsValueTruncated(final boolean valueTruncated) {
    isValueTruncated = valueTruncated;
    return this;
  }

  public String getPreviewValue() {
    return previewValue;
  }

  public VariableDTO setPreviewValue(final String previewValue) {
    this.previewValue = previewValue;
    return this;
  }

  public static VariableDTO createFrom(TaskVariableEntity variableEntity) {
    final VariableDTO variableDTO =
        new VariableDTO().setId(variableEntity.getId()).setName(variableEntity.getName());
    variableDTO
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(variableEntity.getFullValue());
    return variableDTO;
  }

  public static VariableDTO createFrom(VariableEntity variableEntity) {
    final VariableDTO variableDTO =
        new VariableDTO().setId(variableEntity.getId()).setName(variableEntity.getName());
    variableDTO
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(variableEntity.getFullValue());
    return variableDTO;
  }

  public static List<VariableDTO> createFrom(List<VariableEntity> variableEntities) {
    final List<VariableDTO> result = new ArrayList<>();
    if (variableEntities != null) {
      for (VariableEntity variableEntity : variableEntities) {
        if (variableEntity != null) {
          result.add(createFrom(variableEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableDTO that = (VariableDTO) o;
    return isValueTruncated == that.isValueTruncated
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(previewValue, that.previewValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, isValueTruncated, previewValue);
  }
}
