/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.util.CollectionUtil;

public class VariableDto {

  private String id;
  private String name;
  private String value;
  private boolean hasActiveOperation = false;

  @ApiModelProperty(value = "True when variable is the first in current list")
  private boolean isFirst = false;

  /**
   * Sort values, define the position of process instance in the list and may be used to search
   * for previous or following page.
   */
  private String[] sortValues;

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

  public String[] getSortValues() {
    return sortValues;
  }

  public VariableDto setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public static VariableDto createFrom(VariableEntity variableEntity, List<OperationEntity> operations) {
    if (variableEntity == null) {
      return null;
    }
    VariableDto variable = new VariableDto();
    variable.setId(variableEntity.getId());
    variable.setName(variableEntity.getName());
    variable.setValue(variableEntity.getValue());

    if (operations != null && operations.size() > 0) {
      List <OperationEntity> activeOperations = CollectionUtil.filter(operations,(o ->
          o.getState().equals(OperationState.SCHEDULED)
              || o.getState().equals(OperationState.LOCKED)
              || o.getState().equals(OperationState.SENT)));
      if (!activeOperations.isEmpty()) {
        variable.setHasActiveOperation(true);
        variable.setValue(activeOperations.get(activeOperations.size() - 1).getVariableValue());
      }
    }
    //convert to String[]
    if (variableEntity.getSortValues() != null) {
      variable.setSortValues(Arrays.stream(variableEntity.getSortValues())
          .map(String::valueOf)
          .toArray(String[]::new));
    }
    return variable;
  }

  public static List<VariableDto> createFrom(List<VariableEntity> variableEntities, Map<String, List<OperationEntity>> operations) {
    List<VariableDto> result = new ArrayList<>();
    if (variableEntities != null) {
      for (VariableEntity variableEntity: variableEntities) {
        if (variableEntity != null) {
          result.add(createFrom(variableEntity, operations.get(variableEntity.getName())));
        }
      }
    }
    //find new variables
    final Set<String> operationVarNames = operations.keySet();
    if(variableEntities!=null) {
      variableEntities.forEach(ve -> {
        operationVarNames.remove(ve.getName());
      });
    }
    operationVarNames.forEach(varName -> {
      CollectionUtil.addNotNull(result, createFrom(operations.get(varName)));
    });
    return result;
  }

  private static VariableDto createFrom(List<OperationEntity> operations) {
    for (OperationEntity operation: operations) {
      if (operation.getState().equals(OperationState.SCHEDULED)
        || operation.getState().equals(OperationState.LOCKED)
        || operation.getState().equals(OperationState.SENT)) {
        VariableDto variable = new VariableDto();
        variable.setName(operation.getVariableName());
        variable.setValue(operation.getVariableValue());
        variable.setHasActiveOperation(true);
        return variable;
      }
    }
    return null;
  }
}
