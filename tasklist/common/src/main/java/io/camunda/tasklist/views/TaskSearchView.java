/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.views;

import static io.camunda.tasklist.util.CollectionUtil.toArrayOfStrings;

import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskSearchView {

  private String id;
  private String bpmnProcessId;
  private String processDefinitionId;
  private String flowNodeBpmnId;
  private String flowNodeInstanceId;
  private String processInstanceId;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private TaskState state;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private String formKey;
  private String formId;
  private Long formVersion;
  private Boolean isFormEmbedded;
  private String tenantId;
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;
  private boolean first = false;
  private String[] sortValues;
  private TaskImplementation implementation;
  private Integer priority;
  private String externalFormReference;

  public String getId() {
    return id;
  }

  public TaskSearchView setId(String id) {
    this.id = id;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskSearchView setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskSearchView setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskSearchView setFlowNodeBpmnId(String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskSearchView setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskSearchView setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskSearchView setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskSearchView setCompletionTime(OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskSearchView setState(TaskState state) {
    this.state = state;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchView setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchView setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchView setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskSearchView setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskSearchView setFormId(String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskSearchView setFormVersion(Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskSearchView setIsFormEmbedded(Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskSearchView setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchView setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskSearchView setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public boolean isFirst() {
    return first;
  }

  public TaskSearchView setFirst(boolean first) {
    this.first = first;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskSearchView setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskSearchView setImplementation(TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TaskSearchView setExternalFormReference(String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public TaskSearchView setPriority(Integer priority) {
    this.priority = priority;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            id,
            bpmnProcessId,
            processDefinitionId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processInstanceId,
            creationTime,
            completionTime,
            state,
            assignee,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            tenantId,
            followUpDate,
            dueDate,
            first,
            implementation,
            priority,
            externalFormReference);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(sortValues);
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
    if (!super.equals(o)) {
      return false;
    }
    final TaskSearchView that = (TaskSearchView) o;
    return first == that.first
        && implementation == that.implementation
        && Objects.equals(id, that.id)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && state == that.state
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(priority, that.priority)
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(externalFormReference, that.externalFormReference);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchView.class.getSimpleName() + "[", "]")
        .add("bpmnProcessId='" + id + "'")
        .add("bpmnProcessId='" + bpmnProcessId + "'")
        .add("processDefinitionId='" + processDefinitionId + "'")
        .add("flowNodeBpmnId='" + flowNodeBpmnId + "'")
        .add("flowNodeInstanceId='" + flowNodeInstanceId + "'")
        .add("processInstanceId='" + processInstanceId + "'")
        .add("creationTime=" + creationTime)
        .add("completionTime=" + completionTime)
        .add("state=" + state)
        .add("assignee='" + assignee + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded=" + isFormEmbedded)
        .add("tenantId='" + tenantId + "'")
        .add("followUpDate=" + followUpDate)
        .add("dueDate=" + dueDate)
        .add("first=" + first)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("implementation=" + implementation)
        .add("priority=" + priority)
        .toString();
  }

  public static TaskSearchView createFrom(TaskEntity taskEntity, Object[] sortValues) {
    final TaskSearchView taskSearchView =
        new TaskSearchView()
            .setId(String.valueOf(taskEntity.getKey()))
            .setCreationTime(taskEntity.getCreationTime())
            .setCompletionTime(taskEntity.getCompletionTime())
            .setProcessInstanceId(taskEntity.getProcessInstanceId())
            .setState(taskEntity.getState())
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
            .setPriority(taskEntity.getPriority())
            .setExternalFormReference(taskEntity.getExternalFormReference());
    if (sortValues != null) {
      taskSearchView.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskSearchView;
  }
}
