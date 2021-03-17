/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.time.OffsetDateTime;
import java.util.Objects;

public class TaskEntity extends TasklistZeebeEntity<TaskEntity> {

  private String bpmnProcessId;
  private String workflowId;
  private String flowNodeBpmnId;
  private String flowNodeInstanceId;
  private String workflowInstanceId;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private TaskState state;
  private String assignee;
  private String formId;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public TaskEntity setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskEntity setFlowNodeBpmnId(String flowNodeBpmnId) {
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

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public TaskEntity setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskEntity setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskEntity setCompletionTime(OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskEntity setState(TaskState state) {
    this.state = state;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskEntity setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
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
    return Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(workflowId, that.workflowId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(workflowInstanceId, that.workflowInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && state == that.state
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(formId, that.formId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        workflowId,
        flowNodeBpmnId,
        flowNodeInstanceId,
        workflowInstanceId,
        creationTime,
        completionTime,
        state,
        assignee,
        formId);
  }
}
