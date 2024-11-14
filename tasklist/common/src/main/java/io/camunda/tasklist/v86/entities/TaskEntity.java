/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.v86.entities;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class TaskEntity extends TasklistZeebeEntity<TaskEntity> {

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
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;
  private TaskImplementation implementation;
  private String externalFormReference;
  private Map<String, String> customHeaders;
  private Integer processDefinitionVersion;
  private Integer priority;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskEntity setFlowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskEntity setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskEntity setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskEntity setCompletionTime(final OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskEntity setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskEntity setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskEntity setFormKey(final String formId) {
    formKey = formId;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskEntity setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskEntity setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskEntity setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskEntity setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskEntity setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskEntity setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TaskEntity setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public TaskEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public TaskEntity setProcessDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public TaskEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public TaskEntity makeCopy() {
    return new TaskEntity()
        .setId(getId())
        .setKey(getKey())
        .setPartitionId(getPartitionId())
        .setBpmnProcessId(getBpmnProcessId())
        .setProcessDefinitionId(getProcessDefinitionId())
        .setFlowNodeBpmnId(getFlowNodeBpmnId())
        .setFlowNodeInstanceId(getFlowNodeInstanceId())
        .setProcessInstanceId(getProcessInstanceId())
        .setCreationTime(getCreationTime())
        .setCompletionTime(getCompletionTime())
        .setState(getState())
        .setAssignee(getAssignee())
        .setCandidateGroups(getCandidateGroups())
        .setCandidateUsers(getCandidateUsers())
        .setFormKey(getFormKey())
        .setFormId(getFormId())
        .setFormVersion(getFormVersion())
        .setIsFormEmbedded(getIsFormEmbedded())
        .setTenantId(getTenantId())
        .setImplementation(getImplementation())
        .setExternalFormReference(getExternalFormReference())
        .setCustomHeaders(getCustomHeaders())
        .setProcessDefinitionVersion(getProcessDefinitionVersion())
        .setPriority(getPriority());
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
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
            followUpDate,
            dueDate,
            implementation,
            externalFormReference,
            customHeaders,
            processDefinitionVersion,
            priority);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
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
    if (!super.equals(o)) {
      return false;
    }
    final TaskEntity that = (TaskEntity) o;
    return implementation == that.implementation
        && state == that.state
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(externalFormReference, that.externalFormReference)
        && Objects.equals(priority, that.priority);
  }
}
