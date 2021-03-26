/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import static io.zeebe.tasklist.util.CollectionUtil.toArrayOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import java.util.Arrays;
import java.util.Objects;

public final class TaskDTO {

  private String id;
  private String processInstanceId;
  /** Field is used to resolve task name. */
  private String flowNodeBpmnId;

  private String flowNodeInstanceId;
  /** Field is used to resolve process name. */
  private String processId;
  /** Fallback value for process name. */
  private String bpmnProcessId;

  private String creationTime;
  private String completionTime;
  /** Field is used to return user data. */
  private String assigneeUsername;

  private TaskState taskState;

  private String[] sortValues;

  private boolean isFirst = false;

  private String formId;

  public String getId() {
    return id;
  }

  public TaskDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskDTO setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskDTO setFlowNodeBpmnId(String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskDTO setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getProcessId() {
    return processId;
  }

  public TaskDTO setProcessId(String processId) {
    this.processId = processId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskDTO setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TaskDTO setCreationTime(String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TaskDTO setCompletionTime(String completionTime) {
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

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskDTO setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskDTO setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskDTO setIsFirst(final boolean first) {
    isFirst = first;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskDTO setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public static TaskDTO createFrom(TaskEntity taskEntity, ObjectMapper objectMapper) {
    return createFrom(taskEntity, null, objectMapper);
  }

  public static TaskDTO createFrom(
      TaskEntity taskEntity, Object[] sortValues, ObjectMapper objectMapper) {
    final TaskDTO taskDTO =
        new TaskDTO()
            .setCreationTime(objectMapper.convertValue(taskEntity.getCreationTime(), String.class))
            .setCompletionTime(
                objectMapper.convertValue(taskEntity.getCompletionTime(), String.class))
            .setId(taskEntity.getId())
            .setProcessInstanceId(taskEntity.getProcessInstanceId())
            .setTaskState(taskEntity.getState())
            .setAssigneeUsername(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setProcessId(taskEntity.getProcessId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
            .setFormId(taskEntity.getFormId());
    if (sortValues != null) {
      taskDTO.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskDTO;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskDTO taskDTO = (TaskDTO) o;
    return isFirst == taskDTO.isFirst
        && Objects.equals(id, taskDTO.id)
        && Objects.equals(processInstanceId, taskDTO.processInstanceId)
        && Objects.equals(flowNodeBpmnId, taskDTO.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, taskDTO.flowNodeInstanceId)
        && Objects.equals(processId, taskDTO.processId)
        && Objects.equals(bpmnProcessId, taskDTO.bpmnProcessId)
        && Objects.equals(creationTime, taskDTO.creationTime)
        && Objects.equals(completionTime, taskDTO.completionTime)
        && Objects.equals(assigneeUsername, taskDTO.assigneeUsername)
        && taskState == taskDTO.taskState
        && Arrays.equals(sortValues, taskDTO.sortValues)
        && Objects.equals(formId, taskDTO.formId);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            processInstanceId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processId,
            bpmnProcessId,
            creationTime,
            completionTime,
            assigneeUsername,
            taskState,
            isFirst,
            formId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return "TaskDTO{"
        + "id='"
        + id
        + '\''
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", flowNodeBpmnId='"
        + flowNodeBpmnId
        + '\''
        + ", flowNodeInstanceId='"
        + flowNodeInstanceId
        + '\''
        + ", processId='"
        + processId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", creationTime='"
        + creationTime
        + '\''
        + ", completionTime='"
        + completionTime
        + '\''
        + ", assigneeUsername='"
        + assigneeUsername
        + '\''
        + ", taskState="
        + taskState
        + ", sortValues="
        + Arrays.toString(sortValues)
        + ", isFirst="
        + isFirst
        + ", formId='"
        + formId
        + '\''
        + '}';
  }
}
