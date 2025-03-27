/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import static io.camunda.tasklist.webapp.dto.TaskQueryDTO.DEFAULT_PAGE_SIZE;

import io.camunda.tasklist.queries.DateFilter;
import io.camunda.tasklist.queries.RangeValueFilter;
import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskOrderBy;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Objects;

@Schema(description = "Request object to search tasks by provided params.")
public class TaskSearchRequest {
  @Schema(description = "The state of the tasks.")
  private TaskState state;

  @Schema(description = "Are the tasks assigned?")
  private Boolean assigned;

  @Schema(description = "Who is assigned to the tasks?")
  private String assignee;

  @Schema(description = "The assignee is one of the given assignees.")
  private String[] assignees;

  @Schema(description = "What's the BPMN flow node?")
  private String taskDefinitionId;

  @Schema(description = "Given group is in candidate groups list.")
  private String candidateGroup;

  @Schema(description = "At least one of the given groups is in candidate groups list.")
  private String[] candidateGroups;

  @Schema(description = "Given user is in candidate user list.")
  private String candidateUser;

  @Schema(description = "At least one of the given users is in candidate user list.")
  private String[] candidateUsers;

  @Schema(
      description =
          "Reference to process definition (renamed equivalent of TaskQuery.processDefinitionId field).")
  private String processDefinitionKey;

  @Schema(
      description =
          "Reference to process instance (renamed equivalent of TaskQuery.processInstanceId field)")
  private String processInstanceKey;

  @Schema(description = "Size of tasks page (default = 50).")
  private int pageSize = DEFAULT_PAGE_SIZE;

  @Schema(description = "A range of follow-up dates for the tasks to search for.")
  private DateFilter followUpDate;

  @Schema(description = "A range of due dates for the tasks to search for.")
  private DateFilter dueDate;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array of filter clauses specifying the variables to filter for.<br>"
                      + "If defined, the query returns only tasks to which all clauses apply.<br>"
                      + "However, it's important to note that this filtering mechanism is<br>"
                      + "designed to work exclusively with truncated variables. This means<br>"
                      + "variables of a larger size are not compatible with this filter, and<br>"
                      + "attempts to use them may result in inaccurate or incomplete query results."))
  private TaskByVariables[] taskVariables;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array of Tenant IDs to filter tasks. When multi-tenancy is<br>"
                      + "enabled, tasks associated with the specified tenant IDs are returned;<br>"
                      + "if disabled, this parameter is ignored."))
  private String[] tenantIds;

  @ArraySchema(
      arraySchema =
          @Schema(
              description = "An array of objects specifying the fields to sort the results by."))
  private TaskOrderBy[] sort;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Used to return a paginated result. Array of values that should be copied from sortValues of one of the tasks from the current search results page.<br>"
                      + "It enables the API to return a page of tasks that directly follow the task identified by the provided values, with respect to the sorting order."))
  private String[] searchAfter;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Used to return a paginated result. Array of values that should be copied from sortValues of one of the tasks from the current search results page.<br>"
                      + "It enables the API to return a page of tasks that directly follow or are equal to the task identified by the provided values, with respect to the sorting order."))
  private String[] searchAfterOrEqual;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Used to return a paginated result. Array of values that should be copied from sortValues of one of the tasks from the current search results page.<br>"
                      + "It enables the API to return a page of tasks that directly precede the task identified by the provided values, with respect to the sorting order."))
  private String[] searchBefore;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "Used to return a paginated result. Array of values that should be copied from sortValues of one of the tasks from the current search results page.<br>"
                      + "It enables the API to return a page of tasks that directly precede or are equal to the task identified by the provided values, with respect to the sorting order."))
  private String[] searchBeforeOrEqual;

  @ArraySchema(
      arraySchema =
          @Schema(
              description =
                  "An array used to specify a list of variable names that should be included in the response when querying tasks.<br>"
                      + "This field allows users to selectively retrieve specific variables associated with the tasks returned in the search results."))
  private IncludeVariable[] includeVariables;

  private TaskImplementation implementation;

  @Schema(description = "The conditions applied on priority field.")
  private RangeValueFilter priority;

  public TaskState getState() {
    return state;
  }

  public TaskSearchRequest setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskSearchRequest setAssigned(final Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchRequest setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskSearchRequest setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getCandidateGroup() {
    return candidateGroup;
  }

  public TaskSearchRequest setCandidateGroup(final String candidateGroup) {
    this.candidateGroup = candidateGroup;
    return this;
  }

  public String getCandidateUser() {
    return candidateUser;
  }

  public TaskSearchRequest setCandidateUser(final String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskSearchRequest setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskSearchRequest setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public TaskSearchRequest setPageSize(final int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public DateFilter getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchRequest setFollowUpDate(final DateFilter followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilter getDueDate() {
    return dueDate;
  }

  public TaskSearchRequest setDueDate(final DateFilter dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderBy[] getSort() {
    return sort;
  }

  public TaskSearchRequest setSort(final TaskOrderBy[] sort) {
    this.sort = sort;
    return this;
  }

  public TaskByVariables[] getTaskVariables() {
    return taskVariables;
  }

  public TaskSearchRequest setTaskVariables(final TaskByVariables[] taskVariables) {
    this.taskVariables = taskVariables;
    return this;
  }

  public String[] getTenantIds() {
    return tenantIds;
  }

  public TaskSearchRequest setTenantIds(final String[] tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public TaskSearchRequest setSearchAfter(final String[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public TaskSearchRequest setSearchAfterOrEqual(final String[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public TaskSearchRequest setSearchBefore(final String[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public String[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public TaskSearchRequest setSearchBeforeOrEqual(final String[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  public IncludeVariable[] getIncludeVariables() {
    return includeVariables;
  }

  public TaskSearchRequest setIncludeVariables(final IncludeVariable[] includeVariables) {
    this.includeVariables = includeVariables;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskSearchRequest setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String[] getAssignees() {
    return assignees;
  }

  public TaskSearchRequest setAssignees(final String[] assignees) {
    this.assignees = assignees;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchRequest setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchRequest setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public RangeValueFilter getPriority() {
    return priority;
  }

  public TaskSearchRequest setPriority(final RangeValueFilter priority) {
    this.priority = priority;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            state,
            assigned,
            assignee,
            taskDefinitionId,
            candidateGroup,
            candidateUser,
            processDefinitionKey,
            processInstanceKey,
            pageSize,
            followUpDate,
            dueDate,
            implementation,
            priority);
    result = 31 * result + Arrays.hashCode(assignees);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(taskVariables);
    result = 31 * result + Arrays.hashCode(tenantIds);
    result = 31 * result + Arrays.hashCode(sort);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    result = 31 * result + Arrays.hashCode(includeVariables);
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
    final TaskSearchRequest that = (TaskSearchRequest) o;
    return pageSize == that.pageSize
        && state == that.state
        && implementation == that.implementation
        && Objects.equals(assigned, that.assigned)
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(assignees, that.assignees)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(candidateGroup, that.candidateGroup)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUser, that.candidateUser)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(priority, that.priority)
        && Arrays.equals(taskVariables, that.taskVariables)
        && Arrays.equals(tenantIds, that.tenantIds)
        && Arrays.equals(sort, that.sort)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchAfterOrEqual, that.searchAfterOrEqual)
        && Arrays.equals(searchBefore, that.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, that.searchBeforeOrEqual)
        && Arrays.equals(includeVariables, that.includeVariables);
  }

  @Override
  public String toString() {
    return "TaskSearchRequest{"
        + "state="
        + state
        + ", assigned="
        + assigned
        + ", assignee='"
        + assignee
        + '\''
        + ", assignees="
        + Arrays.toString(assignees)
        + ", taskDefinitionId='"
        + taskDefinitionId
        + '\''
        + ", candidateGroup='"
        + candidateGroup
        + '\''
        + ", candidateGroups="
        + Arrays.toString(candidateGroups)
        + ", candidateUser='"
        + candidateUser
        + '\''
        + ", candidateUsers="
        + Arrays.toString(candidateUsers)
        + ", processDefinitionKey='"
        + processDefinitionKey
        + '\''
        + ", processInstanceKey='"
        + processInstanceKey
        + '\''
        + ", pageSize="
        + pageSize
        + ", followUpDate="
        + followUpDate
        + ", dueDate="
        + dueDate
        + ", taskVariables="
        + Arrays.toString(taskVariables)
        + ", tenantIds="
        + Arrays.toString(tenantIds)
        + ", sort="
        + Arrays.toString(sort)
        + ", searchAfter="
        + Arrays.toString(searchAfter)
        + ", searchAfterOrEqual="
        + Arrays.toString(searchAfterOrEqual)
        + ", searchBefore="
        + Arrays.toString(searchBefore)
        + ", searchBeforeOrEqual="
        + Arrays.toString(searchBeforeOrEqual)
        + ", includeVariables="
        + Arrays.toString(includeVariables)
        + ", implementation="
        + implementation
        + ", priority="
        + priority
        + '}';
  }
}
