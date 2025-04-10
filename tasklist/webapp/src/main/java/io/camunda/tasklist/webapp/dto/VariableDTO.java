/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

  public static VariableDTO createFrom(final SnapshotTaskVariableEntity variableEntity) {
    final VariableDTO variableDTO =
        new VariableDTO().setId(variableEntity.getId()).setName(variableEntity.getName());
    variableDTO
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(variableEntity.getFullValue());
    return variableDTO;
  }

  public static VariableDTO createFrom(final VariableEntity variableEntity) {
    final VariableDTO variableDTO =
        new VariableDTO().setId(variableEntity.getId()).setName(variableEntity.getName());
    variableDTO
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(
            variableEntity.getIsPreview()
                ? variableEntity.getFullValue()
                : variableEntity.getValue());
    return variableDTO;
  }

  public static List<VariableDTO> createFrom(final List<VariableEntity> variableEntities) {
    final List<VariableDTO> result = new ArrayList<>();
    if (variableEntities != null) {
      for (final VariableEntity variableEntity : variableEntities) {
        if (variableEntity != null) {
          result.add(createFrom(variableEntity));
        }
      }
    }
    return result;
  }

  public static List<VariableDTO> createFromTaskVariables(
      final List<SnapshotTaskVariableEntity> taskVariableEntities) {
    return taskVariableEntities.stream().map(VariableDTO::createFrom).collect(Collectors.toList());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, isValueTruncated, previewValue);
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
}
