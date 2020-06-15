/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import static io.zeebe.tasklist.util.CollectionUtil.map;

public final class TaskDTO {

  private String id;
  private String name;
  private String workflowName;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private String assigneeUsername;
  private List<VariableDTO> variables = new ArrayList<>();
  private TaskState taskState;

  public String getId() {
    return id;
  }

  public TaskDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public TaskDTO setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskDTO setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskDTO setCompletionTime(OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getAssigneeUsername() {
    return assigneeUsername;
  }

  public TaskDTO setAssigneeUsername(String assigneeUsername) {
    this.assigneeUsername = assigneeUsername;
    return this;
  }

  public List<VariableDTO> getVariables() {
    return variables;
  }

  public TaskDTO setVariables(List<VariableDTO> variables) {
    this.variables = variables;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskDTO setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public static TaskDTO createFrom(TaskEntity taskEntity) {
    return new TaskDTO()
        .setCompletionTime(taskEntity.getCompletionTime())
        .setCreationTime(taskEntity.getCreationTime())
        .setId(taskEntity.getId())
        .setTaskState(taskEntity.getState())
        .setAssigneeUsername(taskEntity.getAssignee())
        //TODO #40
        .setName(taskEntity.getElementId())
        .setWorkflowName(taskEntity.getBpmnProcessId());
    //        .setVariables()
  }

  public static List<TaskDTO> createFrom(List<TaskEntity> taskEntities) {
    return map(taskEntities, t -> createFrom(t));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    TaskDTO task = (TaskDTO) o;

    if (id != null ? !id.equals(task.id) : task.id != null)
      return false;
    if (name != null ? !name.equals(task.name) : task.name != null)
      return false;
    if (workflowName != null ? !workflowName.equals(task.workflowName) : task.workflowName != null)
      return false;
    if (creationTime != null ? !creationTime.equals(task.creationTime) : task.creationTime != null)
      return false;
    if (completionTime != null ? !completionTime.equals(task.completionTime) : task.completionTime != null)
      return false;
    if (assigneeUsername != null ? !assigneeUsername.equals(task.assigneeUsername) : task.assigneeUsername != null)
      return false;
    if (variables != null ? !variables.equals(task.variables) : task.variables != null)
      return false;
    return taskState == task.taskState;

  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (workflowName != null ? workflowName.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (completionTime != null ? completionTime.hashCode() : 0);
    result = 31 * result + (assigneeUsername != null ? assigneeUsername.hashCode() : 0);
    result = 31 * result + (variables != null ? variables.hashCode() : 0);
    result = 31 * result + (taskState != null ? taskState.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "TaskDTO{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", workflowName='" + workflowName + '\'' + ", creationTime=" + creationTime
        + ", completionTime=" + completionTime + ", assigneeUsername='" + assigneeUsername + '\'' + ", variables=" + variables + ", taskState=" + taskState
        + '}';
  }
}
