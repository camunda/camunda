/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.queries;

import io.camunda.tasklist.entities.TaskState;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskQuery {

  private TaskState state;
  private Boolean assigned;
  private String assignee;
  private String taskDefinitionId;
  private String candidateGroup;
  private String candidateUser;
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

  public TaskState getState() {
    return state;
  }

  public TaskQuery setState(TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskQuery setAssigned(Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskQuery setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskQuery setTaskDefinitionId(String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getCandidateGroup() {
    return candidateGroup;
  }

  public TaskQuery setCandidateGroup(String candidateGroup) {
    this.candidateGroup = candidateGroup;
    return this;
  }

  public String getCandidateUser() {
    return candidateUser;
  }

  public TaskQuery setCandidateUser(String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskQuery setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskQuery setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public TaskByVariables[] getTaskVariables() {
    return taskVariables;
  }

  public TaskQuery setTaskVariables(TaskByVariables[] taskVariables) {
    this.taskVariables = taskVariables;
    return this;
  }

  public String[] getTenantIds() {
    return tenantIds;
  }

  public TaskQuery setTenantIds(String[] tenantIds) {
    this.tenantIds = tenantIds;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public TaskQuery setPageSize(int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public TaskQuery setSearchAfter(String[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public TaskQuery setSearchAfterOrEqual(String[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public TaskQuery setSearchBefore(String[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public String[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public TaskQuery setSearchBeforeOrEqual(String[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
    return this;
  }

  public DateFilter getFollowUpDate() {
    return followUpDate;
  }

  public TaskQuery setFollowUpDate(DateFilter followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilter getDueDate() {
    return dueDate;
  }

  public TaskQuery setDueDate(DateFilter dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderBy[] getSort() {
    return sort;
  }

  public TaskQuery setSort(TaskOrderBy[] sort) {
    this.sort = sort;
    return this;
  }

  public TaskByCandidateUserOrGroup getTaskByCandidateUserOrGroups() {
    return taskByCandidateUserOrGroups;
  }

  public TaskQuery setTaskByCandidateUserOrGroups(
      TaskByCandidateUserOrGroup taskByCandidateUserOrGroups) {
    this.taskByCandidateUserOrGroups = taskByCandidateUserOrGroups;
    return this;
  }

  public TaskQuery createCopy() {
    return new TaskQuery()
        .setAssigned(this.assigned)
        .setAssignee(this.assignee)
        .setTaskDefinitionId(this.taskDefinitionId)
        .setPageSize(this.pageSize)
        .setSearchAfter(this.searchAfter)
        .setSearchAfterOrEqual(this.searchAfterOrEqual)
        .setSearchBefore(this.searchBefore)
        .setSearchBeforeOrEqual(this.searchBeforeOrEqual)
        .setState(this.state)
        .setTaskVariables(this.taskVariables)
        .setTenantIds(this.tenantIds)
        .setCandidateGroup(this.candidateGroup)
        .setTaskByCandidateUserOrGroups(this.taskByCandidateUserOrGroups);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TaskQuery taskQuery = (TaskQuery) o;
    return pageSize == taskQuery.pageSize
        && state == taskQuery.state
        && Objects.equals(assigned, taskQuery.assigned)
        && Objects.equals(assignee, taskQuery.assignee)
        && Objects.equals(taskDefinitionId, taskQuery.taskDefinitionId)
        && Objects.equals(candidateGroup, taskQuery.candidateGroup)
        && Objects.equals(candidateUser, taskQuery.candidateUser)
        && Objects.equals(processDefinitionId, taskQuery.processDefinitionId)
        && Objects.equals(processInstanceId, taskQuery.processInstanceId)
        && Objects.equals(taskByCandidateUserOrGroups, taskQuery.taskByCandidateUserOrGroups)
        && Arrays.equals(taskVariables, taskQuery.taskVariables)
        && Arrays.equals(tenantIds, taskQuery.tenantIds)
        && Arrays.equals(searchAfter, taskQuery.searchAfter)
        && Arrays.equals(searchAfterOrEqual, taskQuery.searchAfterOrEqual)
        && Arrays.equals(searchBefore, taskQuery.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, taskQuery.searchBeforeOrEqual)
        && Objects.equals(followUpDate, taskQuery.followUpDate)
        && Objects.equals(dueDate, taskQuery.dueDate)
        && Arrays.equals(sort, taskQuery.sort);
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
            taskByCandidateUserOrGroups,
            processDefinitionId,
            processInstanceId,
            pageSize,
            followUpDate,
            dueDate);
    result = 31 * result + Arrays.hashCode(tenantIds);
    result = 31 * result + Arrays.hashCode(taskVariables);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    result = 31 * result + Arrays.hashCode(sort);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskQuery.class.getSimpleName() + "[", "]")
        .add("state=" + state)
        .add("assigned=" + assigned)
        .add("assignee='" + assignee + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("candidateGroup='" + candidateGroup + "'")
        .add("candidateUser='" + candidateUser + "'")
        .add("candidateUserAndGroups='" + taskByCandidateUserOrGroups + "'")
        .add("processDefinitionId='" + processDefinitionId + "'")
        .add("processInstanceId='" + processInstanceId + "'")
        .add("pageSize=" + pageSize)
        .add("taskVariables=" + Arrays.toString(taskVariables))
        .add("tenantIds=" + Arrays.toString(tenantIds))
        .add("searchAfter=" + Arrays.toString(searchAfter))
        .add("searchAfterOrEqual=" + Arrays.toString(searchAfterOrEqual))
        .add("searchBefore=" + Arrays.toString(searchBefore))
        .add("searchBeforeOrEqual=" + Arrays.toString(searchBeforeOrEqual))
        .add("followUpDate=" + followUpDate)
        .add("dueDate=" + dueDate)
        .add("sort=" + Arrays.toString(sort))
        .toString();
  }
}
