/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

import java.time.OffsetDateTime;

public class TaskEntity extends TasklistZeebeEntity<TaskEntity> {

  private String bpmnProcessId;
  private String elementId;
  private String workflowInstanceKey;     //TODO is this String or Long? If String may be rename to workflowInstanceId?
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private TaskState state;
  private String assignee;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public TaskEntity setElementId(String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public TaskEntity setWorkflowInstanceKey(String workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    TaskEntity that = (TaskEntity) o;

    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (elementId != null ? !elementId.equals(that.elementId) : that.elementId != null)
      return false;
    if (workflowInstanceKey != null ? !workflowInstanceKey.equals(that.workflowInstanceKey) : that.workflowInstanceKey != null)
      return false;
    if (creationTime != null ? !creationTime.equals(that.creationTime) : that.creationTime != null)
      return false;
    if (completionTime != null ? !completionTime.equals(that.completionTime) : that.completionTime != null)
      return false;
    if (state != that.state)
      return false;
    return assignee != null ? assignee.equals(that.assignee) : that.assignee == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + (workflowInstanceKey != null ? workflowInstanceKey.hashCode() : 0);
    result = 31 * result + (creationTime != null ? creationTime.hashCode() : 0);
    result = 31 * result + (completionTime != null ? completionTime.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (assignee != null ? assignee.hashCode() : 0);
    return result;
  }
}
