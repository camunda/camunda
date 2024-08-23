/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;

public class TaskVariableSnapshotEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String taskId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeBpmnId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String completionTime;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long position;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String state;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long key;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String creationTime;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String bpmnProcessId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processDefinitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String assignee;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateGroups;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateUsers;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long formVersion;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isFormEmbedded = null;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String followUpDate;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String dueDate;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String tenantId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String implementation;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String externalFormReference;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer processDefinitionVersion;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> customHeaders;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer priority;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String variableName;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String variableValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String variableFullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isPreview = null;

  private Map<String, Object> joinField; // Add this field

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

  public TaskVariableSnapshotEntity setProcessDefinitionVersion(
      final Integer processDefinitionVersion) {
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

  // Getters and setters for all fields
  public Map<String, Object> getJoinField() {
    return joinField;
  }

  public TaskVariableSnapshotEntity setJoinField(final Map<String, Object> joinField) {
    this.joinField = joinField;
    return this;
  }
}
