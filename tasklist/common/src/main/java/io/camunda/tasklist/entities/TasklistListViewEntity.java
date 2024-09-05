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
import io.camunda.tasklist.entities.listview.ProcessInstanceListViewEntity;
import io.camunda.tasklist.entities.listview.UserTaskListViewEntity;
import io.camunda.tasklist.entities.listview.VariableListViewEntity;

public class TasklistListViewEntity {
  private String dataType;

  // This is only used to persist the Parent/Child relation
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ProcessInstanceListViewEntity processInstanceListViewEntity;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private UserTaskListViewEntity userTaskListViewEntity;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private VariableListViewEntity variableListViewEntity;

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

  public ProcessInstanceListViewEntity getProcessInstanceEntity() {
    return processInstanceListViewEntity;
  }

  public TasklistListViewEntity setProcessInstanceEntity(
      final ProcessInstanceListViewEntity processInstanceListViewEntity) {
    this.processInstanceListViewEntity = processInstanceListViewEntity;
    return this;
  }

  public UserTaskListViewEntity getUserTaskEntity() {
    return userTaskListViewEntity;
  }

  public TasklistListViewEntity setUserTaskEntity(final UserTaskListViewEntity userTaskListViewEntity) {
    this.userTaskListViewEntity = userTaskListViewEntity;
    return this;
  }

  public VariableListViewEntity getVariableEntity() {
    return variableListViewEntity;
  }

  public TasklistListViewEntity setVariableEntity(final VariableListViewEntity variableListViewEntity) {
    this.variableListViewEntity = variableListViewEntity;
    return this;
  }
}
