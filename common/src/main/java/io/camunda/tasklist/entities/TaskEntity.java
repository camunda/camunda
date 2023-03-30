/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.time.OffsetDateTime;
import java.util.Arrays;
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
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskEntity setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
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

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskEntity setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
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
    this.formKey = formId;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskEntity setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskEntity setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskEntity setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public TaskEntity makeCopy() {
    return new TaskEntity()
        .setId(this.getId())
        .setKey(this.getKey())
        .setPartitionId(this.getPartitionId())
        .setBpmnProcessId(this.getBpmnProcessId())
        .setProcessDefinitionId(this.getProcessDefinitionId())
        .setFlowNodeBpmnId(this.getFlowNodeBpmnId())
        .setFlowNodeInstanceId(this.getFlowNodeInstanceId())
        .setProcessInstanceId(this.getProcessInstanceId())
        .setCreationTime(this.getCreationTime())
        .setCompletionTime(this.getCompletionTime())
        .setState(this.getState())
        .setAssignee(this.getAssignee())
        .setCandidateGroups(this.getCandidateGroups())
        .setCandidateUsers(this.getCandidateUsers())
        .setFormKey(this.getFormKey());
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
    final TaskEntity that = (TaskEntity) o;
    return Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && state == that.state
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(formKey, that.formKey);
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
            followUpDate,
            dueDate);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    return result;
  }
}
