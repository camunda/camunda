/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import static io.camunda.tasklist.util.CollectionUtil.toArrayOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public final class TaskDTO {

  private String id;
  private String processInstanceId;
  /** Field is used to resolve task name. */
  private String flowNodeBpmnId;

  private String flowNodeInstanceId;
  /** Field is used to resolve process name. */
  private String processDefinitionId;
  /** Fallback value for process name. */
  private String bpmnProcessId;

  private String creationTime;
  private String completionTime;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private TaskState taskState;

  private String[] sortValues;

  private boolean isFirst = false;

  private String formKey;

  private OffsetDateTime dueDate;

  private OffsetDateTime followUpDate;

  public String getId() {
    return id;
  }

  public TaskDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskDTO setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskDTO setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
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

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskDTO setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
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

  public String[] candidateUsers() {
    return candidateUsers;
  }

  public TaskDTO setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskDTO setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskDTO setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskDTO setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
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
            .setAssignee(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setProcessDefinitionId(taskEntity.getProcessDefinitionId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
            .setFormKey(taskEntity.getFormKey())
            .setCandidateGroups(taskEntity.getCandidateGroups())
            .setFollowUpDate(taskEntity.getFollowUpDate())
            .setDueDate(taskEntity.getDueDate())
            .setCandidateUsers(taskEntity.getCandidateUsers());

    if (sortValues != null) {
      taskDTO.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskDTO;
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
    return isFirst == taskDTO.isFirst
        && Objects.equals(id, taskDTO.id)
        && Objects.equals(processInstanceId, taskDTO.processInstanceId)
        && Objects.equals(flowNodeBpmnId, taskDTO.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, taskDTO.flowNodeInstanceId)
        && Objects.equals(processDefinitionId, taskDTO.processDefinitionId)
        && Objects.equals(bpmnProcessId, taskDTO.bpmnProcessId)
        && Objects.equals(creationTime, taskDTO.creationTime)
        && Objects.equals(completionTime, taskDTO.completionTime)
        && Objects.equals(assignee, taskDTO.assignee)
        && Arrays.equals(candidateGroups, taskDTO.candidateGroups)
        && Arrays.equals(candidateUsers, taskDTO.candidateUsers)
        && taskState == taskDTO.taskState
        && Arrays.equals(sortValues, taskDTO.sortValues)
        && Objects.equals(formKey, taskDTO.formKey)
        && Objects.equals(dueDate, taskDTO.dueDate)
        && Objects.equals(followUpDate, taskDTO.followUpDate);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            processInstanceId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processDefinitionId,
            bpmnProcessId,
            creationTime,
            completionTime,
            assignee,
            taskState,
            isFirst,
            formKey,
            dueDate,
            followUpDate);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
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
        + processDefinitionId
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
        + ", assignee='"
        + assignee
        + '\''
        + ", taskState="
        + taskState
        + ", sortValues="
        + Arrays.toString(sortValues)
        + ", isFirst="
        + isFirst
        + ", formId='"
        + formKey
        + '\''
        + ", followUpDate='"
        + followUpDate
        + '\''
        + ", dueDate='"
        + dueDate
        + ", candidateUsers='"
        + candidateUsers
        + '\''
        + ", candidateGroups='"
        + candidateGroups
        + '\''
        + '}';
  }
}
