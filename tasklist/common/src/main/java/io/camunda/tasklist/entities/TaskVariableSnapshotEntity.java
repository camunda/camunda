/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskVariableSnapshotEntity {
  private String id;
  private String taskId;
  private String flowNodeBpmnId;
  private String flowNodeInstanceId;
  private Integer partitionId;
  private String completionTime;
  private String processInstanceId;
  private Long position;
  private String state;
  private Long key;
  private String creationTime;
  private String bpmnProcessId;
  private String processDefinitionId;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private String formKey;
  private String formId;
  private Long formVersion;
  private Boolean isFormEmbedded;
  private String followUpDate;
  private String dueDate;
  private String tenantId;
  private String implementation;
  private String externalFormReference;
  private Integer processDefinitionVersion;
  private Map<String, String> customHeaders;
  private Integer priority;
  private String variableName;
  private String variableValue;
  private String variableFullValue;
  private Boolean isPreview;

  public String getId() {
    return id;
  }

  public TaskVariableSnapshotEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getTaskId() {
    return taskId;
  }

  public TaskVariableSnapshotEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskVariableSnapshotEntity setFlowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskVariableSnapshotEntity setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public TaskVariableSnapshotEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TaskVariableSnapshotEntity setCompletionTime(final String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskVariableSnapshotEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public TaskVariableSnapshotEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public String getState() {
    return state;
  }

  public TaskVariableSnapshotEntity setState(final String state) {
    this.state = state;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public TaskVariableSnapshotEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TaskVariableSnapshotEntity setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskVariableSnapshotEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskVariableSnapshotEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskVariableSnapshotEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskVariableSnapshotEntity setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskVariableSnapshotEntity setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskVariableSnapshotEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskVariableSnapshotEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskVariableSnapshotEntity setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskVariableSnapshotEntity setFormEmbedded(final Boolean formEmbedded) {
    isFormEmbedded = formEmbedded;
    return this;
  }

  public String getFollowUpDate() {
    return followUpDate;
  }

  public TaskVariableSnapshotEntity setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String getDueDate() {
    return dueDate;
  }

  public TaskVariableSnapshotEntity setDueDate(final String dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskVariableSnapshotEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getImplementation() {
    return implementation;
  }

  public TaskVariableSnapshotEntity setImplementation(final String implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TaskVariableSnapshotEntity setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public TaskVariableSnapshotEntity setProcessDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public TaskVariableSnapshotEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public TaskVariableSnapshotEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public String getVariableName() {
    return variableName;
  }

  public TaskVariableSnapshotEntity setVariableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public TaskVariableSnapshotEntity setVariableValue(final String variableValue) {
    this.variableValue = variableValue;
    return this;
  }

  public String getVariableFullValue() {
    return variableFullValue;
  }

  public TaskVariableSnapshotEntity setVariableFullValue(final String variableFullValue) {
    this.variableFullValue = variableFullValue;
    return this;
  }

  public Boolean getPreview() {
    return isPreview;
  }

  public TaskVariableSnapshotEntity setPreview(final Boolean preview) {
    isPreview = preview;
    return this;
  }
}
