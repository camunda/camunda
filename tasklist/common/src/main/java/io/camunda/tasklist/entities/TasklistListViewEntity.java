/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public class TasklistListViewEntity {
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
  private TaskState state;

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
  private String varName;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String varValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String varFullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isPreview = null;

  // add variableScopeKey
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String varScopeKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private DocumentNodeType dataType;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> join; // Add this field

  public String getId() {
    return id;
  }

  public TasklistListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getTaskId() {
    return taskId;
  }

  public TasklistListViewEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TasklistListViewEntity setFlowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TasklistListViewEntity setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public TasklistListViewEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public TasklistListViewEntity setCompletionTime(final String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TasklistListViewEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public TasklistListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TasklistListViewEntity setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public TasklistListViewEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public TasklistListViewEntity setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TasklistListViewEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TasklistListViewEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TasklistListViewEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TasklistListViewEntity setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TasklistListViewEntity setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TasklistListViewEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFollowUpDate() {
    return followUpDate;
  }

  public TasklistListViewEntity setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String getDueDate() {
    return dueDate;
  }

  public TasklistListViewEntity setDueDate(final String dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TasklistListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getImplementation() {
    return implementation;
  }

  public TasklistListViewEntity setImplementation(final String implementation) {
    this.implementation = implementation;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TasklistListViewEntity setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public TasklistListViewEntity setProcessDefinitionVersion(
      final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public TasklistListViewEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public TasklistListViewEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public String getVarName() {
    return varName;
  }

  public TasklistListViewEntity setVarName(final String varName) {
    this.varName = varName;
    return this;
  }

  public String getVarValue() {
    return varValue;
  }

  public TasklistListViewEntity setVarValue(final String varValue) {
    this.varValue = varValue;
    return this;
  }

  public String getVarFullValue() {
    return varFullValue;
  }

  public TasklistListViewEntity setVarFullValue(final String varFullValue) {
    this.varFullValue = varFullValue;
    return this;
  }

  public Boolean getIsPreview() {
    return isPreview;
  }

  public TasklistListViewEntity setIsPreview(final Boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  // Getters and setters for variableScopeKey
  public String getVarScopeKey() {
    return varScopeKey;
  }

  public TasklistListViewEntity setVarScopeKey(final String varScopeKey) {
    this.varScopeKey = varScopeKey;
    return this;
  }

  // Getters and setters for dataype
  public DocumentNodeType getDataType() {
    return dataType;
  }

  public TasklistListViewEntity setDataType(final DocumentNodeType dataType) {
    this.dataType = dataType;
    return this;
  }

  // Getters and setters for all fields
  public Map<String, Object> getJoin() {
    return join;
  }

  public TasklistListViewEntity setJoin(final Map<String, Object> join) {
    this.join = join;
    return this;
  }
}
