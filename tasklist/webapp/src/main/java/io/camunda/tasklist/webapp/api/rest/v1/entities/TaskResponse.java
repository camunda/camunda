/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskResponse {

  @Schema(description = "The unique identifier of the task.")
  private String id;

  @Schema(description = "The name of the task.")
  private String name;

  @Schema(description = "User Task ID from the BPMN definition.")
  private String taskDefinitionId;

  @Schema(description = "The name of the process.")
  private String processName;

  @Schema(
      description = "When was the task created (renamed equivalent of `Task.creationTime` field).")
  private String creationDate;

  @Schema(
      description =
          "When was the task completed (renamed equivalent of `Task.completionTime` field).")
  private String completionDate;

  @Schema(description = "The username/id of who is assigned to the task.")
  private String assignee;

  @Schema(description = "The state of the task.", accessMode = AccessMode.READ_ONLY)
  private TaskState taskState;

  @Schema(description = "Reference to the task form.")
  private String formKey;

  @Schema(
      description =
          "Reference to the ID of a deployed form. If the form is not deployed, this property is null.")
  private String formId;

  @Schema(
      description =
          "Reference to the version of a deployed form. If the form is not deployed, this property is null.",
      format = "int64")
  private Long formVersion;

  @Schema(
      description =
          "Is the form embedded for this task? If there is no form, this property is null.")
  private Boolean isFormEmbedded;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of `Task.processDefinitionId` field).")
  private String processDefinitionKey;

  @Schema(
      description =
          "Reference to process instance id (renamed equivalent of `Task.processInstanceId` field).")
  private String processInstanceKey;

  @Schema(description = "The tenant ID associated with the task.")
  private String tenantId;

  @Schema(description = "The due date for the task.", format = "date-time")
  private OffsetDateTime dueDate;

  @Schema(description = "The follow-up date for the task.", format = "date-time")
  private OffsetDateTime followUpDate;

  @ArraySchema(arraySchema = @Schema(description = "The candidate groups for the task."))
  private String[] candidateGroups;

  @ArraySchema(arraySchema = @Schema(description = "The candidate users for the task."))
  private String[] candidateUsers;

  private TaskImplementation implementation;

  @Schema(description = "The assigned priority of the task. Only for Zeebe User Tasks.")
  private int priority;

  public String getId() {
    return id;
  }

  public TaskResponse setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskResponse setName(final String name) {
    this.name = name;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskResponse setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public TaskResponse setProcessName(final String processName) {
    this.processName = processName;
    return this;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public TaskResponse setCreationDate(final String creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public TaskResponse setCompletionDate(final String completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskResponse setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskResponse setTaskState(final TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskResponse setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskResponse setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskResponse setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskResponse setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskResponse setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskResponse setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskResponse setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskResponse setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskResponse setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskResponse setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskResponse setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskResponse setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public int getPriority() {
    return priority;
  }

  public TaskResponse setPriority(final int priority) {
    this.priority = priority;
    return this;
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
            formId,
            formVersion,
            isFormEmbedded,
            processDefinitionKey,
            processInstanceKey,
            tenantId,
            dueDate,
            followUpDate,
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
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && priority == that.priority
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers);
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
        .add("formId='" + formId + "'")
        .add("formId='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate='" + dueDate + "'")
        .add("followUpDate='" + followUpDate + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("priority=" + priority)
        .toString();
  }
}
