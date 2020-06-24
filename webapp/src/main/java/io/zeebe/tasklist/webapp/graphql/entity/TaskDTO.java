/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import static io.zeebe.tasklist.util.CollectionUtil.map;

import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TaskDTO {

  private String id;
  private String elementId;
  private String workflowId;
  private String bpmnProcessId;
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

  public String getElementId() {
    return elementId;
  }

  public TaskDTO setElementId(String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public TaskDTO setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskDTO setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
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
        .setBpmnProcessId(taskEntity.getBpmnProcessId())
        .setWorkflowId(taskEntity.getWorkflowId())
        .setElementId(taskEntity.getElementId());
  }

  public static List<TaskDTO> createFrom(List<TaskEntity> taskEntities) {
    return map(taskEntities, t -> createFrom(t));
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + (workflowId != null ? workflowId.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (completionTime != null ? completionTime.hashCode() : 0);
    result = 31 * result + (assigneeUsername != null ? assigneeUsername.hashCode() : 0);
    result = 31 * result + (variables != null ? variables.hashCode() : 0);
    result = 31 * result + (taskState != null ? taskState.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final TaskDTO taskDTO = (TaskDTO) o;

    if (id != null ? !id.equals(taskDTO.id) : taskDTO.id != null) {
      return false;
    }
    if (elementId != null ? !elementId.equals(taskDTO.elementId) : taskDTO.elementId != null) {
      return false;
    }
    if (workflowId != null ? !workflowId.equals(taskDTO.workflowId) : taskDTO.workflowId != null) {
      return false;
    }
    if (bpmnProcessId != null
        ? !bpmnProcessId.equals(taskDTO.bpmnProcessId)
        : taskDTO.bpmnProcessId != null) {
      return false;
    }
    if (creationTime != null
        ? !creationTime.equals(taskDTO.creationTime)
        : taskDTO.creationTime != null) {
      return false;
    }
    if (completionTime != null
        ? !completionTime.equals(taskDTO.completionTime)
        : taskDTO.completionTime != null) {
      return false;
    }
    if (assigneeUsername != null
        ? !assigneeUsername.equals(taskDTO.assigneeUsername)
        : taskDTO.assigneeUsername != null) {
      return false;
    }
    if (variables != null ? !variables.equals(taskDTO.variables) : taskDTO.variables != null) {
      return false;
    }
    return taskState == taskDTO.taskState;
  }

  @Override
  public String toString() {
    return "TaskDTO{"
        + "id='"
        + id
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", workflowId='"
        + workflowId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", creationTime="
        + creationTime
        + ", completionTime="
        + completionTime
        + ", assigneeUsername='"
        + assigneeUsername
        + '\''
        + ", variables="
        + variables
        + ", taskState="
        + taskState
        + '}';
  }
}
