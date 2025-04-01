/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import io.camunda.tasklist.queries.*;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TaskQueryDTO {

  public static final int DEFAULT_PAGE_SIZE = 50;
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
  private TaskByVariables[] taskVariables;
  private String[] tenantIds;
  private int pageSize = DEFAULT_PAGE_SIZE;
  private String[] searchAfter;
  private String[] searchAfterOrEqual;
  private String[] searchBefore;
  private String[] searchBeforeOrEqual;
  private DateFilter followUpDate;
  private DateFilter dueDate;
  private TaskOrderBy[] sort;
  private TaskByCandidateUserOrGroup taskByCandidateUserOrGroup;
  private TaskImplementation implementation;
  private RangeValueFilter priority;

  public TaskQueryDTO(
      final TaskState state,
      final Boolean assigned,
      final String assignee,
      final String taskDefinitionId,
      final String candidateGroup,
      final String candidateUser,
      final String processDefinitionId,
      final String processInstanceId,
      final Integer pageSize,
      final List<String> searchAfter,
      final List<String> searchAfterOrEqual,
      final List<String> searchBefore,
      final List<String> searchBeforeOrEqual,
      final DateFilter followUpDate,
      final DateFilter dueDate,
      final TaskOrderBy[] sort,
      final TaskImplementation implementation) {
    this.state = state;
    this.assigned = assigned;
    this.assignee = assignee;
    this.taskDefinitionId = taskDefinitionId;
    this.candidateGroup = candidateGroup;
    this.candidateUser = candidateUser;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.pageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
    this.searchAfter = searchAfter != null ? searchAfter.toArray(new String[0]) : null;
    this.searchAfterOrEqual =
        searchAfterOrEqual != null ? searchAfterOrEqual.toArray(new String[0]) : null;
    this.searchBefore = searchBefore != null ? searchBefore.toArray(new String[0]) : null;
    this.searchBeforeOrEqual =
        searchBeforeOrEqual != null ? searchBeforeOrEqual.toArray(new String[0]) : null;
    this.followUpDate = followUpDate;
    this.dueDate = dueDate;
    this.sort = sort;
    this.implementation = implementation;
    priority = priority;
  }

  public TaskQueryDTO() {}

  public TaskState getState() {
    return state;
  }

  public TaskQueryDTO setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskQueryDTO setAssigned(final Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskQueryDTO setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskByVariables[] getTaskVariables() {
    return taskVariables;
  }

  public TaskQueryDTO setTaskVariables(final TaskByVariables[] taskVariables) {
    this.taskVariables = taskVariables;
    return this;
  }

  public String[] getTenantIds() {
    return tenantIds;
  }

  public TaskQueryDTO setTenantIds(final String[] tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public TaskQueryDTO setPageSize(final int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public TaskQueryDTO setSearchAfter(final String[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public TaskQueryDTO setSearchAfterOrEqual(final String[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public TaskQueryDTO setSearchBefore(final String[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public String[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public TaskQueryDTO setSearchBeforeOrEqual(final String[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskQueryDTO setTaskDefinitionId(final String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getCandidateGroup() {
    return candidateGroup;
  }

  public TaskQueryDTO setCandidateGroup(final String candidateGroup) {
    this.candidateGroup = candidateGroup;
    return this;
  }

  public String getCandidateUser() {
    return candidateUser;
  }

  public TaskQueryDTO setCandidateUser(final String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskQueryDTO setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskQueryDTO setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public DateFilter getFollowUpDate() {
    return followUpDate;
  }

  public TaskQueryDTO setFollowUpDate(final DateFilter followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilter getDueDate() {
    return dueDate;
  }

  public TaskQueryDTO setDueDate(final DateFilter dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderBy[] getSort() {
    return sort;
  }

  public TaskQueryDTO setSort(final TaskOrderBy[] sort) {
    this.sort = sort;
    return this;
  }

  public TaskByCandidateUserOrGroup getTaskByCandidateUserOrGroup() {
    return taskByCandidateUserOrGroup;
  }

  public TaskQueryDTO setTaskByCandidateUserOrGroup(
      final TaskByCandidateUserOrGroup taskByCandidateUserOrGroup) {
    this.taskByCandidateUserOrGroup = taskByCandidateUserOrGroup;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskQueryDTO setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String[] getAssignees() {
    return assignees;
  }

  public TaskQueryDTO setAssignees(final String[] assignees) {
    this.assignees = assignees;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskQueryDTO setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskQueryDTO setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public RangeValueFilter getPriority() {
    return priority;
  }

  public TaskQueryDTO setPriority(final RangeValueFilter priority) {
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
            processDefinitionId,
            processInstanceId,
            pageSize,
            followUpDate,
            dueDate,
            taskByCandidateUserOrGroup,
            implementation,
            priority);
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
    final TaskQueryDTO that = (TaskQueryDTO) o;
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
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Arrays.equals(taskVariables, that.taskVariables)
        && Arrays.equals(tenantIds, that.tenantIds)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchAfterOrEqual, that.searchAfterOrEqual)
        && Arrays.equals(searchBefore, that.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, that.searchBeforeOrEqual)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(sort, that.sort)
        && Objects.equals(taskByCandidateUserOrGroup, that.taskByCandidateUserOrGroup)
        && Objects.equals(priority, that.priority);
  }

  @Override
  public String toString() {
    return "TaskQueryDTO{"
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
        + ", taskVariables="
        + Arrays.toString(taskVariables)
        + ", tenantIds="
        + Arrays.toString(tenantIds)
        + ", pageSize="
        + pageSize
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
        + ", taskByCandidateUserOrGroup="
        + taskByCandidateUserOrGroup
        + ", implementation="
        + implementation
        + ", priority="
        + priority
        + '}';
  }

  public TaskQuery toTaskQuery() {
    return new TaskQuery()
        .setState(state)
        .setAssigned(assigned)
        .setAssignee(assignee)
        .setAssignees(assignees)
        .setTaskDefinitionId(taskDefinitionId)
        .setCandidateGroup(candidateGroup)
        .setCandidateGroups(candidateGroups)
        .setCandidateUser(candidateUser)
        .setCandidateUsers(candidateUsers)
        .setTaskByCandidateUserOrGroups(taskByCandidateUserOrGroup)
        .setProcessDefinitionId(processDefinitionId)
        .setProcessInstanceId(processInstanceId)
        .setPageSize(pageSize)
        .setTaskVariables(taskVariables)
        .setTenantIds(tenantIds)
        .setSearchAfter(searchAfter)
        .setSearchAfterOrEqual(searchAfterOrEqual)
        .setSearchBefore(searchBefore)
        .setSearchBefore(searchBefore)
        .setSearchBeforeOrEqual(searchBeforeOrEqual)
        .setFollowUpDate(followUpDate)
        .setDueDate(dueDate)
        .setSort(sort)
        .setImplementation(implementation)
        .setPriority(priority);
  }
}
