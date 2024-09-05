/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.entities.listview.ListViewJoinRelation;
import io.camunda.tasklist.entities.listview.ProcessInstanceEntity;
import io.camunda.tasklist.entities.listview.UserTaskEntity;
import io.camunda.tasklist.entities.listview.VariableEntity;

public class TasklistListViewEntity {
  private String dataType;

  // This is only used to persist the Parent/Child relation
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ProcessInstanceEntity processInstanceEntity;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private UserTaskEntity userTaskEntity;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private VariableEntity variableEntity;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ListViewJoinRelation listViewJoinRelation;

  // Get and Set methods
  public String getDataType() {
    return dataType;
  }

  public TasklistListViewEntity setDataType(final String dataType) {
    this.dataType = dataType;
    return this;
  }

  public ProcessInstanceEntity getProcessInstanceEntity() {
    return processInstanceEntity;
  }

  public TasklistListViewEntity setProcessInstanceEntity(
      final ProcessInstanceEntity processInstanceEntity) {
    this.processInstanceEntity = processInstanceEntity;
    return this;
  }

  public UserTaskEntity getUserTaskEntity() {
    return userTaskEntity;
  }

  public TasklistListViewEntity setUserTaskEntity(final UserTaskEntity userTaskEntity) {
    this.userTaskEntity = userTaskEntity;
    return this;
  }

  public VariableEntity getVariableEntity() {
    return variableEntity;
  }

  public TasklistListViewEntity setVariableEntity(final VariableEntity variableEntity) {
    this.variableEntity = variableEntity;
    return this;
  }
}
