/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.queries.*;
import java.util.Arrays;
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

  public TaskState getState() {
    return state;
  }

  public TaskQueryDTO setState(TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskQueryDTO setAssigned(Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskQueryDTO setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public TaskByVariables[] getTaskVariables() {
    return taskVariables;
  }

  public TaskQueryDTO setTaskVariables(TaskByVariables[] taskVariables) {
    this.taskVariables = taskVariables;
    return this;
  }

  public String[] getTenantIds() {
    return tenantIds;
  }

  public TaskQueryDTO setTenantIds(String[] tenantIds) {
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

  public TaskQueryDTO setTaskDefinitionId(String taskDefinitionId) {
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

  public TaskQueryDTO setCandidateUser(String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskQueryDTO setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskQueryDTO setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public DateFilter getFollowUpDate() {
    return followUpDate;
  }

  public TaskQueryDTO setFollowUpDate(DateFilter followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilter getDueDate() {
    return dueDate;
  }

  public TaskQueryDTO setDueDate(DateFilter dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderBy[] getSort() {
    return sort;
  }

  public TaskQueryDTO setSort(TaskOrderBy[] sort) {
    this.sort = sort;
    return this;
  }

  public TaskByCandidateUserOrGroup getTaskByCandidateUserOrGroup() {
    return taskByCandidateUserOrGroup;
  }

  public TaskQueryDTO setTaskByCandidateUserOrGroup(
      TaskByCandidateUserOrGroup taskByCandidateUserOrGroup) {
    this.taskByCandidateUserOrGroup = taskByCandidateUserOrGroup;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskQueryDTO setImplementation(TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public String[] getAssignees() {
    return assignees;
  }

  public TaskQueryDTO setAssignees(String[] assignees) {
    this.assignees = assignees;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskQueryDTO setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskQueryDTO setCandidateUsers(String[] candidateUsers) {
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
        && Objects.equals(taskByCandidateUserOrGroup, that.taskByCandidateUserOrGroup);
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
            implementation);
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

  public TaskQuery toTaskQuery() {
    return new TaskQuery()
        .setState(this.state)
        .setAssigned(this.assigned)
        .setAssignee(this.assignee)
        .setAssignees(this.assignees)
        .setTaskDefinitionId(this.taskDefinitionId)
        .setCandidateGroup(this.candidateGroup)
        .setCandidateGroups(this.candidateGroups)
        .setCandidateUser(this.candidateUser)
        .setCandidateUsers(this.candidateUsers)
        .setTaskByCandidateUserOrGroups(this.taskByCandidateUserOrGroup)
        .setProcessDefinitionId(this.processDefinitionId)
        .setProcessInstanceId(this.processInstanceId)
        .setPageSize(this.pageSize)
        .setTaskVariables(this.taskVariables)
        .setTenantIds(this.tenantIds)
        .setSearchAfter(this.searchAfter)
        .setSearchAfterOrEqual(this.searchAfterOrEqual)
        .setSearchBefore(this.searchBefore)
        .setSearchBefore(this.searchBefore)
        .setSearchBeforeOrEqual(this.searchBeforeOrEqual)
        .setFollowUpDate(this.followUpDate)
        .setDueDate(this.dueDate)
        .setSort(this.sort)
        .setImplementation(this.implementation);
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
        + '}';
  }
}
