/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import static io.camunda.tasklist.util.CollectionUtil.toArrayOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

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
  private String formId;
  private Long formVersion;
  private Boolean isFormEmbedded;
  private String tenantId;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private String externalFormReference;

  private VariableDTO[] variables;

  private TaskImplementation implementation;

  private int priority;

  public String getId() {
    return id;
  }

  public TaskDTO setId(final String id) {
    this.id = id;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskDTO setAssignee(final String assignee) {
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

  public TaskDTO setFlowNodeBpmnId(final String flowNodeBpmnId) {
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

  public TaskDTO setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskDTO setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TaskDTO setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TaskDTO setCompletionTime(final String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskDTO setTaskState(final TaskState taskState) {
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

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskDTO setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskDTO setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskDTO setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskDTO setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskDTO setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskDTO setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskDTO setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskDTO setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public VariableDTO[] getVariables() {
    return variables;
  }

  public TaskDTO setVariables(final VariableDTO[] variables) {
    this.variables = variables;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskDTO setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public int getPriority() {
    return priority;
  }

  public TaskDTO setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TaskDTO setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public static TaskDTO createFrom(final TaskEntity taskEntity, final ObjectMapper objectMapper) {
    return createFrom(taskEntity, null, objectMapper);
  }

  public static TaskDTO createFrom(
      final TaskEntity taskEntity, final Object[] sortValues, final ObjectMapper objectMapper) {
    final TaskDTO taskDTO =
        new TaskDTO()
            .setCreationTime(objectMapper.convertValue(taskEntity.getCreationTime(), String.class))
            .setCompletionTime(
                objectMapper.convertValue(taskEntity.getCompletionTime(), String.class))
            .setId(String.valueOf(taskEntity.getKey()))
            .setProcessInstanceId(taskEntity.getProcessInstanceId())
            .setTaskState(taskEntity.getState())
            .setAssignee(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setProcessDefinitionId(taskEntity.getProcessDefinitionId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
            .setFormKey(taskEntity.getFormKey())
            .setFormId(taskEntity.getFormId())
            .setFormVersion(taskEntity.getFormVersion())
            .setIsFormEmbedded(taskEntity.getIsFormEmbedded())
            .setTenantId(taskEntity.getTenantId())
            .setFollowUpDate(taskEntity.getFollowUpDate())
            .setDueDate(taskEntity.getDueDate())
            .setCandidateGroups(taskEntity.getCandidateGroups())
            .setCandidateUsers(taskEntity.getCandidateUsers())
            .setImplementation(taskEntity.getImplementation())
            .setExternalFormReference(taskEntity.getExternalFormReference())
            .setPriority(taskEntity.getPriority() == null ? 50 : taskEntity.getPriority());
    if (sortValues != null) {
      taskDTO.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskDTO;
  }

  public static TaskDTO createFrom(
      final TaskSearchView taskSearchView,
      final VariableDTO[] variables,
      final ObjectMapper objectMapper) {
    return new TaskDTO()
        .setCreationTime(objectMapper.convertValue(taskSearchView.getCreationTime(), String.class))
        .setCompletionTime(
            objectMapper.convertValue(taskSearchView.getCompletionTime(), String.class))
        .setId(taskSearchView.getId())
        .setProcessInstanceId(taskSearchView.getProcessInstanceId())
        .setTaskState(taskSearchView.getState())
        .setAssignee(taskSearchView.getAssignee())
        .setBpmnProcessId(taskSearchView.getBpmnProcessId())
        .setProcessDefinitionId(taskSearchView.getProcessDefinitionId())
        .setFlowNodeBpmnId(taskSearchView.getFlowNodeBpmnId())
        .setFlowNodeInstanceId(taskSearchView.getFlowNodeInstanceId())
        .setFormKey(taskSearchView.getFormKey())
        .setFormId(taskSearchView.getFormId())
        .setFormVersion(taskSearchView.getFormVersion())
        .setIsFormEmbedded(taskSearchView.getIsFormEmbedded())
        .setTenantId(taskSearchView.getTenantId())
        .setFollowUpDate(taskSearchView.getFollowUpDate())
        .setDueDate(taskSearchView.getDueDate())
        .setCandidateGroups(taskSearchView.getCandidateGroups())
        .setCandidateUsers(taskSearchView.getCandidateUsers())
        .setSortValues(taskSearchView.getSortValues())
        .setIsFirst(taskSearchView.isFirst())
        .setVariables(variables)
        .setImplementation(taskSearchView.getImplementation())
        .setPriority(taskSearchView.getPriority() == null ? 50 : taskSearchView.getPriority())
        .setExternalFormReference(taskSearchView.getExternalFormReference());
  }

  public static TaskEntity toTaskEntity(final TaskDTO taskDTO) {

    final TaskEntity taskEntity;
    try {
      taskEntity =
          new TaskEntity()
              .setCreationTime(
                  DateUtil.toOffsetDateTime(
                      DateUtil.SIMPLE_DATE_FORMAT.parse(taskDTO.getCreationTime()).toInstant()))
              .setKey(Long.parseLong(taskDTO.getId()))
              .setProcessInstanceId(taskDTO.getProcessInstanceId())
              .setState(taskDTO.getTaskState())
              .setAssignee(taskDTO.getAssignee())
              .setBpmnProcessId(taskDTO.getBpmnProcessId())
              .setProcessDefinitionId(taskDTO.getProcessDefinitionId())
              .setFlowNodeBpmnId(taskDTO.getFlowNodeBpmnId())
              .setFlowNodeInstanceId(taskDTO.getFlowNodeInstanceId())
              .setFormKey(taskDTO.getFormKey())
              .setFormId(taskDTO.getFormId())
              .setFormVersion(taskDTO.getFormVersion())
              .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
              .setTenantId(taskDTO.getTenantId())
              .setFollowUpDate(taskDTO.getFollowUpDate())
              .setDueDate(taskDTO.getDueDate())
              .setCandidateGroups(taskDTO.getCandidateGroups())
              .setCandidateUsers(taskDTO.getCandidateUsers())
              .setImplementation(taskDTO.getImplementation())
              .setPriority(taskDTO.getPriority())
              .setExternalFormReference(taskDTO.getExternalFormReference());

      if (taskDTO.getCompletionTime() != null) {
        taskEntity.setCompletionTime(
            DateUtil.toOffsetDateTime(
                DateUtil.SIMPLE_DATE_FORMAT.parse(taskDTO.getCompletionTime()).toInstant()));
      }
    } catch (final ParseException e) {
      throw new TasklistRuntimeException(e);
    }

    return taskEntity;
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
            formId,
            formVersion,
            isFormEmbedded,
            tenantId,
            dueDate,
            followUpDate,
            implementation,
            priority,
            externalFormReference);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(sortValues);
    result = 31 * result + Arrays.hashCode(variables);
    return result;
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
        && implementation == taskDTO.implementation
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
        && Objects.equals(formId, taskDTO.formId)
        && Objects.equals(formVersion, taskDTO.formVersion)
        && Objects.equals(isFormEmbedded, taskDTO.isFormEmbedded)
        && Objects.equals(tenantId, taskDTO.tenantId)
        && Objects.equals(dueDate, taskDTO.dueDate)
        && Objects.equals(followUpDate, taskDTO.followUpDate)
        && priority == taskDTO.priority
        && Arrays.equals(variables, taskDTO.variables)
        && Objects.equals(externalFormReference, taskDTO.externalFormReference);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskDTO.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("processInstanceId='" + processInstanceId + "'")
        .add("flowNodeBpmnId='" + flowNodeBpmnId + "'")
        .add("flowNodeInstanceId='" + flowNodeInstanceId + "'")
        .add("processDefinitionId='" + processDefinitionId + "'")
        .add("bpmnProcessId='" + bpmnProcessId + "'")
        .add("creationTime='" + creationTime + "'")
        .add("completionTime='" + completionTime + "'")
        .add("assignee='" + assignee + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("taskState=" + taskState)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("isFirst=" + isFirst)
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate=" + dueDate)
        .add("followUpDate=" + followUpDate)
        .add("variables=" + Arrays.toString(variables))
        .add("implementation=" + implementation)
        .add("priority=" + priority)
        .add("externalFormReference='" + externalFormReference + "'")
        .toString();
  }
}
