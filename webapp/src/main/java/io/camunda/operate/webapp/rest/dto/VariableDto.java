/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Arrays;
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
   * Sort values, define the position of process instance in the list and may be used to search
   * for previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
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

  public void setHasActiveOperation(boolean hasActiveOperation) {
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

  public static VariableDto createFrom(VariableEntity variableEntity, List<OperationEntity> operations,
      boolean fullValue, int variableSizeThreshold, ObjectMapper objectMapper) {
    if (variableEntity == null) {
      return null;
    }
    VariableDto variable = new VariableDto();
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
      List <OperationEntity> activeOperations = CollectionUtil.filter(operations,(o ->
          o.getState().equals(OperationState.SCHEDULED)
              || o.getState().equals(OperationState.LOCKED)
              || o.getState().equals(OperationState.SENT)));
      if (!activeOperations.isEmpty()) {
        variable.setHasActiveOperation(true);
        final String newValue = activeOperations.get(activeOperations.size() - 1)
            .getVariableValue();
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

    //convert to String[]
    if (variableEntity.getSortValues() != null) {
      variable.setSortValues(SortValuesWrapper.createFrom(variableEntity.getSortValues(), objectMapper));
    }
    return variable;
  }

  public static List<VariableDto> createFrom(List<VariableEntity> variableEntities,
      Map<String, List<OperationEntity>> operations, int variableSizeThreshold, ObjectMapper objectMapper) {
    if (variableEntities == null) {
      return new ArrayList<>();
    }
    return variableEntities.stream().filter(item -> item != null)
        .map(item -> createFrom(
            item,
            operations.get(item.getName()),
            false,
            variableSizeThreshold, objectMapper))
        .collect(Collectors.toList());
  }

}
