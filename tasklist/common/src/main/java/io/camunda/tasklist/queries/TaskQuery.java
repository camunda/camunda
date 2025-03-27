/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.queries;

import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.util.Arrays;
import java.util.Objects;

public class TaskQuery {

  private TaskState state;
  private Boolean assigned;
  private String assignee;
  private String[] assignees;
  private String taskDefinitionId;
  private String candidateGroup;
  private String[] candidateGroups;
  private String candidateUser;
  private String[] candidateUsers;
  private String processDefinitionId;
  private String processInstanceId;
  private int pageSize;
  private TaskByVariables[] taskVariables;
  private String[] tenantIds;
  private String[] searchAfter;
  private String[] searchAfterOrEqual;
  private String[] searchBefore;
  private String[] searchBeforeOrEqual;
  private DateFilter followUpDate;
  private DateFilter dueDate;
  private TaskOrderBy[] sort;
  private TaskByCandidateUserOrGroup taskByCandidateUserOrGroups;
  private TaskImplementation implementation;
  private RangeValueFilter priority;

  public TaskState getState() {
    return state;
  }

  public TaskQuery setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskQuery setAssigned(final Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskQuery setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskQuery setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getCandidateGroup() {
    return candidateGroup;
  }

  public TaskQuery setCandidateGroup(final String candidateGroup) {
    this.candidateGroup = candidateGroup;
    return this;
  }

  public String getCandidateUser() {
    return candidateUser;
  }

  public TaskQuery setCandidateUser(final String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskQuery setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskQuery setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public TaskByVariables[] getTaskVariables() {
    return taskVariables;
  }

  public TaskQuery setTaskVariables(final TaskByVariables[] taskVariables) {
    this.taskVariables = taskVariables;
    return this;
  }

  public String[] getTenantIds() {
    return tenantIds;
  }

  public TaskQuery setTenantIds(final String[] tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public TaskQuery setPageSize(final int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public TaskQuery setSearchAfter(final String[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public TaskQuery setSearchAfterOrEqual(final String[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public TaskQuery setSearchBefore(final String[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public String[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public TaskQuery setSearchBeforeOrEqual(final String[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  public DateFilter getFollowUpDate() {
    return followUpDate;
  }

  public TaskQuery setFollowUpDate(final DateFilter followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilter getDueDate() {
    return dueDate;
  }

  public TaskQuery setDueDate(final DateFilter dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderBy[] getSort() {
    return sort;
  }

  public TaskQuery setSort(final TaskOrderBy[] sort) {
    this.sort = sort;
    return this;
  }

  public String[] getAssignees() {
    return assignees;
  }

  public TaskQuery setAssignees(final String[] assignees) {
    this.assignees = assignees;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskQuery setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskQuery setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public TaskByCandidateUserOrGroup getTaskByCandidateUserOrGroups() {
    return taskByCandidateUserOrGroups;
  }

  public TaskQuery setTaskByCandidateUserOrGroups(
      final TaskByCandidateUserOrGroup taskByCandidateUserOrGroups) {
    this.taskByCandidateUserOrGroups = taskByCandidateUserOrGroups;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskQuery setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public RangeValueFilter getPriority() {
    return priority;
  }

  public TaskQuery setPriority(final RangeValueFilter priority) {
    this.priority = priority;
    return this;
  }

  public TaskQuery createCopy() {
    return new TaskQuery()
        .setAssigned(assigned)
        .setAssignee(assignee)
        .setTaskDefinitionId(taskDefinitionId)
        .setPageSize(pageSize)
        .setSearchAfter(searchAfter)
        .setSearchAfterOrEqual(searchAfterOrEqual)
        .setSearchBefore(searchBefore)
        .setSearchBeforeOrEqual(searchBeforeOrEqual)
        .setState(state)
        .setTaskVariables(taskVariables)
        .setTenantIds(tenantIds)
        .setCandidateGroup(candidateGroup)
        .setTaskByCandidateUserOrGroups(taskByCandidateUserOrGroups)
        .setImplementation(implementation)
        .setAssignees(assignees)
        .setCandidateGroups(candidateGroups)
        .setCandidateUsers(candidateUsers)
        .setPriority(priority);
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
            processDefinitionId,
            processInstanceId,
            pageSize,
            followUpDate,
            dueDate,
            taskByCandidateUserOrGroups,
            implementation,
            priority);
    result = 31 * result + Arrays.hashCode(tenantIds);
    result = 31 * result + Arrays.hashCode(assignees);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(taskVariables);
    result = 31 * result + Arrays.hashCode(tenantIds);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    result = 31 * result + Arrays.hashCode(sort);
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
    final TaskQuery taskQuery = (TaskQuery) o;
    return pageSize == taskQuery.pageSize
        && state == taskQuery.state
        && implementation == taskQuery.implementation
        && Objects.equals(assigned, taskQuery.assigned)
        && Objects.equals(assignee, taskQuery.assignee)
        && Arrays.equals(assignees, taskQuery.assignees)
        && Objects.equals(taskDefinitionId, taskQuery.taskDefinitionId)
        && Objects.equals(candidateGroup, taskQuery.candidateGroup)
        && Arrays.equals(candidateGroups, taskQuery.candidateGroups)
        && Objects.equals(candidateUser, taskQuery.candidateUser)
        && Arrays.equals(candidateUsers, taskQuery.candidateUsers)
        && Objects.equals(processDefinitionId, taskQuery.processDefinitionId)
        && Objects.equals(processInstanceId, taskQuery.processInstanceId)
        && Arrays.equals(taskVariables, taskQuery.taskVariables)
        && Arrays.equals(tenantIds, taskQuery.tenantIds)
        && Arrays.equals(searchAfter, taskQuery.searchAfter)
        && Arrays.equals(searchAfterOrEqual, taskQuery.searchAfterOrEqual)
        && Arrays.equals(searchBefore, taskQuery.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, taskQuery.searchBeforeOrEqual)
        && Objects.equals(followUpDate, taskQuery.followUpDate)
        && Objects.equals(dueDate, taskQuery.dueDate)
        && Objects.equals(priority, taskQuery.priority)
        && Objects.equals(taskByCandidateUserOrGroups, taskQuery.taskByCandidateUserOrGroups)
        && Arrays.equals(sort, taskQuery.sort);
  }

  @Override
  public String toString() {
    return "TaskQuery{"
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
        + ", processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", pageSize="
        + pageSize
        + ", taskVariables="
        + Arrays.toString(taskVariables)
        + ", tenantIds="
        + Arrays.toString(tenantIds)
        + ", searchAfter="
        + Arrays.toString(searchAfter)
        + ", searchAfterOrEqual="
        + Arrays.toString(searchAfterOrEqual)
        + ", searchBefore="
        + Arrays.toString(searchBefore)
        + ", searchBeforeOrEqual="
        + Arrays.toString(searchBeforeOrEqual)
        + ", followUpDate="
        + followUpDate
        + ", dueDate="
        + dueDate
        + ", sort="
        + Arrays.toString(sort)
        + ", taskByCandidateUserOrGroups="
        + taskByCandidateUserOrGroups
        + ", implementation="
        + implementation
        + ", priority="
        + priority
        + '}';
  }
}
