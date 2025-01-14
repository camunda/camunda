/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VariableDto {

  private String id;
  private String name;
  private String value;
  private boolean isPreview;
  private boolean hasActiveOperation = false;

  @Schema(description = "True when variable is the first in current list")
  private boolean isFirst = false;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  public static VariableDto createFrom(
      final VariableEntity variableEntity,
      final List<OperationEntity> operations,
      final boolean fullValue,
      final int variableSizeThreshold,
      final ObjectMapper objectMapper) {
    if (variableEntity == null) {
      return null;
    }
    final VariableDto variable = new VariableDto();
    variable.setId(variableEntity.getId());
    variable.setName(variableEntity.getName());

    if (fullValue) {
      if (variableEntity.getFullValue() != null) {
        variable.setValue(variableEntity.getFullValue());
      } else {
        variable.setValue(variableEntity.getValue());
      }
      variable.setIsPreview(false);
    } else {
      variable.setValue(variableEntity.getValue());
      variable.setIsPreview(variableEntity.getIsPreview());
    }

    if (CollectionUtil.isNotEmpty(operations)) {
      final List<OperationEntity> activeOperations =
          CollectionUtil.filter(
              operations,
              (o ->
                  o.getState().equals(OperationState.SCHEDULED)
                      || o.getState().equals(OperationState.LOCKED)
                      || o.getState().equals(OperationState.SENT)));
      if (!activeOperations.isEmpty()) {
        variable.setHasActiveOperation(true);
        final String newValue =
            activeOperations.get(activeOperations.size() - 1).getVariableValue();
        if (fullValue) {
          variable.setValue(newValue);
        } else if (newValue.length() > variableSizeThreshold) {
          // set preview
          variable.setValue(newValue.substring(0, variableSizeThreshold));
          variable.setIsPreview(true);
        } else {
          variable.setValue(newValue);
        }
      }
    }

    // convert to String[]
    if (variableEntity.getSortValues() != null) {
      variable.setSortValues(
          SortValuesWrapper.createFrom(variableEntity.getSortValues(), objectMapper));
    }
    return variable;
  }

  public static List<VariableDto> createFrom(
      final List<VariableEntity> variableEntities,
      final Map<String, List<OperationEntity>> operations,
      final int variableSizeThreshold,
      final ObjectMapper objectMapper) {
    if (variableEntities == null) {
      return new ArrayList<>();
    }
    return variableEntities.stream()
        .filter(item -> item != null)
        .map(
            item ->
                createFrom(
                    item,
                    operations.get(item.getName()),
                    false,
                    variableSizeThreshold,
                    objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public VariableDto setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public boolean isHasActiveOperation() {
    return hasActiveOperation;
  }

  public void setHasActiveOperation(final boolean hasActiveOperation) {
    this.hasActiveOperation = hasActiveOperation;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public VariableDto setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public VariableDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }
}
