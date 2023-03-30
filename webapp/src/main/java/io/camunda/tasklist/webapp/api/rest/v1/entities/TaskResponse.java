/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.TaskState;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskResponse {
  private String id;
  private String name;
  private String taskDefinitionId;
  private String processName;
  private String creationDate;
  private String completionDate;
  private String assignee;
  private TaskState taskState;
  private String formKey;
  private String processDefinitionKey;
  private String processInstanceKey;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private String[] candidateGroups;
  private String[] candidateUsers;

  public String getId() {
    return id;
  }

  public TaskResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskResponse setTaskDefinitionId(String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public TaskResponse setProcessName(String processName) {
    this.processName = processName;
    return this;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public TaskResponse setCreationDate(String creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public TaskResponse setCompletionDate(String completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskResponse setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskResponse setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskResponse setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskResponse setProcessInstanceKey(String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskResponse setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskResponse setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskResponse setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskResponse setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskResponse that = (TaskResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(completionDate, that.completionDate)
        && Objects.equals(assignee, that.assignee)
        && taskState == that.taskState
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            name,
            taskDefinitionId,
            processName,
            creationDate,
            completionDate,
            assignee,
            taskState,
            formKey,
            processDefinitionKey,
            processInstanceKey,
            dueDate,
            followUpDate);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("processName='" + processName + "'")
        .add("creationDate='" + creationDate + "'")
        .add("completionDate='" + completionDate + "'")
        .add("assignee='" + assignee + "'")
        .add("taskState=" + taskState)
        .add("formKey='" + formKey + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("dueDate='" + dueDate + "'")
        .add("followUpDate='" + followUpDate + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .toString();
  }
}
