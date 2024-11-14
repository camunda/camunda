/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.v86.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.v86.entities.TaskEntity;
import io.camunda.tasklist.v86.entities.TaskState;
import java.util.Map;

public class UserTaskListViewEntity {
  // This is identified as the flowNodeInstanceId
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
  private String externalFormReference;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer processDefinitionVersion;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> customHeaders;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer priority;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ListViewJoinRelation join;

  public UserTaskListViewEntity(final TaskEntity taskEntity) {
    setId(taskEntity.getFlowNodeInstanceId());
    setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId());
    setProcessInstanceId(taskEntity.getProcessInstanceId());
    setTaskId(taskEntity.getFlowNodeBpmnId());
    setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId());
    setKey(taskEntity.getKey());
    setPartitionId(taskEntity.getPartitionId());
    setCompletionTime(
        taskEntity.getCompletionTime() != null ? taskEntity.getCompletionTime().toString() : null);
    setAssignee(taskEntity.getAssignee());
    setCreationTime(
        taskEntity.getCreationTime() != null ? taskEntity.getCreationTime().toString() : null);
    setProcessDefinitionVersion(taskEntity.getProcessDefinitionVersion());
    setPriority(taskEntity.getPriority());
    setCandidateGroups(taskEntity.getCandidateGroups());
    setCandidateUsers(taskEntity.getCandidateUsers());
    setBpmnProcessId(taskEntity.getBpmnProcessId());
    setProcessDefinitionId(taskEntity.getProcessDefinitionId());
    setTenantId(taskEntity.getTenantId());
    setExternalFormReference(taskEntity.getExternalFormReference());
    setCustomHeaders(taskEntity.getCustomHeaders());
    setFormKey(taskEntity.getFormKey());
    setState(taskEntity.getState());

    // Set the join field for the parent
    final ListViewJoinRelation joinRelation = new ListViewJoinRelation();
    joinRelation.setName("task");
    joinRelation.setParent(Long.valueOf(taskEntity.getProcessInstanceId()));
    setJoin(joinRelation);
  }

  public String getId() {
    return id;
  }

  public UserTaskListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getTaskId() {
    return taskId;
  }

  public UserTaskListViewEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public UserTaskListViewEntity setFlowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public UserTaskListViewEntity setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public UserTaskListViewEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getCompletionTime() {
    return completionTime;
  }

  public UserTaskListViewEntity setCompletionTime(final String completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public UserTaskListViewEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public UserTaskListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public UserTaskListViewEntity setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public UserTaskListViewEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public UserTaskListViewEntity setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public UserTaskListViewEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public UserTaskListViewEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public UserTaskListViewEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public UserTaskListViewEntity setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public UserTaskListViewEntity setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public UserTaskListViewEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFollowUpDate() {
    return followUpDate;
  }

  public UserTaskListViewEntity setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String getDueDate() {
    return dueDate;
  }

  public UserTaskListViewEntity setDueDate(final String dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UserTaskListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public UserTaskListViewEntity setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public UserTaskListViewEntity setProcessDefinitionVersion(
      final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public UserTaskListViewEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public UserTaskListViewEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public ListViewJoinRelation getJoin() {
    return join;
  }

  public UserTaskListViewEntity setJoin(final ListViewJoinRelation join) {
    this.join = join;
    return this;
  }
}
