/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.TaskState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskSearchResponse {
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

  @Schema(description = "The state of the task.")
  private TaskState taskState;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Array of values to be copied into `TaskSearchRequest` to request for next or previous page of tasks."))
  private String[] sortValues;

  @Schema(description = "A flag to show that the task is first in the current filter.")
  private boolean isFirst;

  @Schema(description = "Reference to the task form.")
  private String formKey;

  @Schema(
      description =
          "Reference to the ID of a deployed form. If the form is not deployed, this property is null.")
  private String formId;

  @Schema(
      description =
          "Reference to the version of a deployed form. If the form is not deployed, this property is null.")
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

  @Schema(description = "The due date for the task.")
  private OffsetDateTime dueDate;

  @Schema(description = "The follow-up date for the task.")
  private OffsetDateTime followUpDate;

  @ArraySchema(arraySchema = @Schema(description = "The candidate groups for the task."))
  private String[] candidateGroups;

  @ArraySchema(arraySchema = @Schema(description = "The candidate users for the task."))
  private String[] candidateUsers;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array of the task's variables. Only variables specified in `TaskSearchRequest.includeVariables` are returned. Note that a variable's draft value is not returned in `TaskSearchResponse`."))
  private VariableSearchResponse[] variables;

  public String getId() {
    return id;
  }

  public TaskSearchResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskSearchResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskSearchResponse setTaskDefinitionId(String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getProcessName() {
    return processName;
  }

  public TaskSearchResponse setProcessName(String processName) {
    this.processName = processName;
    return this;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public TaskSearchResponse setCreationDate(String creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public String getCompletionDate() {
    return completionDate;
  }

  public TaskSearchResponse setCompletionDate(String completionDate) {
    this.completionDate = completionDate;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchResponse setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public TaskSearchResponse setTaskState(TaskState taskState) {
    this.taskState = taskState;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskSearchResponse setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public boolean getIsFirst() {
    return isFirst;
  }

  public TaskSearchResponse setIsFirst(boolean first) {
    isFirst = first;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskSearchResponse setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskSearchResponse setFormId(String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskSearchResponse setFormVersion(Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskSearchResponse setIsFormEmbedded(Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskSearchResponse setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskSearchResponse setProcessInstanceKey(String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public TaskSearchResponse setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskSearchResponse setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchResponse setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchResponse setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchResponse setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public VariableSearchResponse[] getVariables() {
    return variables;
  }

  public TaskSearchResponse setVariables(VariableSearchResponse[] variables) {
    this.variables = variables;
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
    final TaskSearchResponse that = (TaskSearchResponse) o;
    return isFirst == that.isFirst
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(processName, that.processName)
        && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(completionDate, that.completionDate)
        && Objects.equals(assignee, that.assignee)
        && taskState == that.taskState
        && Arrays.equals(sortValues, that.sortValues)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Arrays.equals(variables, that.variables);
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
            isFirst,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            processDefinitionKey,
            processInstanceKey,
            tenantId,
            dueDate,
            followUpDate);
    result = 31 * result + Arrays.hashCode(sortValues);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(variables);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("processName='" + processName + "'")
        .add("creationDate='" + creationDate + "'")
        .add("completionDate='" + completionDate + "'")
        .add("assignee='" + assignee + "'")
        .add("taskState=" + taskState)
        .add("sortValues=" + Arrays.toString(sortValues))
        .add("isFirst=" + isFirst)
        .add("formKey='" + formKey + "'")
        .add("formId='" + formId + "'")
        .add("formVersion='" + formVersion + "'")
        .add("isFormEmbedded='" + isFormEmbedded + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("tenantId='" + tenantId + "'")
        .add("dueDate='" + dueDate + "'")
        .add("followUpDate='" + followUpDate + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("variables=" + Arrays.toString(variables))
        .toString();
  }
}
