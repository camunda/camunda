/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.detailview;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.VariableEntity;

public class VariableDto {

  private String id;
  private String name;
  private String value;
  private String scopeId;
  private String workflowInstanceId;

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

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }


  public static VariableDto createFrom(VariableEntity variableEntity) {
    if (variableEntity == null) {
      return null;
    }
    VariableDto variable = new VariableDto();
    variable.setId(variableEntity.getId());
    variable.setName(variableEntity.getName());
    variable.setValue(variableEntity.getValue());
    variable.setScopeId(variableEntity.getScopeId());
    variable.setWorkflowInstanceId(variableEntity.getWorkflowInstanceId());
    return variable;
  }

  public static List<VariableDto> createFrom(List<VariableEntity> variableEntities) {
    List<VariableDto> result = new ArrayList<>();
    if (variableEntities != null) {
      for (VariableEntity variableEntity: variableEntities) {
        if (variableEntity != null) {
          result.add(createFrom(variableEntity));
        }
      }
    }
    return result;
  }
}
