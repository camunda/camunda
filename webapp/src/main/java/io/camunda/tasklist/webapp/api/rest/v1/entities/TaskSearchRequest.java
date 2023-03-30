/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import static io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO.DEFAULT_PAGE_SIZE;

import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.webapp.graphql.entity.DateFilterDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskOrderByDTO;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskSearchRequest {
  private TaskState state;
  private Boolean assigned;
  private String assignee;
  private String taskDefinitionId;
  private String candidateGroup;
  private String candidateUser;
  private String processDefinitionKey;
  private String processInstanceKey;
  private int pageSize = DEFAULT_PAGE_SIZE;
  private DateFilterDTO followUpDate;
  private DateFilterDTO dueDate;
  private TaskOrderByDTO[] sort;
  private String[] searchAfter;
  private String[] searchAfterOrEqual;
  private String[] searchBefore;
  private String[] searchBeforeOrEqual;

  public TaskState getState() {
    return state;
  }

  public TaskSearchRequest setState(TaskState state) {
    this.state = state;
    return this;
  }

  public Boolean getAssigned() {
    return assigned;
  }

  public TaskSearchRequest setAssigned(Boolean assigned) {
    this.assigned = assigned;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchRequest setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String getTaskDefinitionId() {
    return taskDefinitionId;
  }

  public TaskSearchRequest setTaskDefinitionId(String taskDefinitionId) {
    this.taskDefinitionId = taskDefinitionId;
    return this;
  }

  public String getCandidateGroup() {
    return candidateGroup;
  }

  public TaskSearchRequest setCandidateGroup(String candidateGroup) {
    this.candidateGroup = candidateGroup;
    return this;
  }

  public String getCandidateUser() {
    return candidateUser;
  }

  public TaskSearchRequest setCandidateUser(String candidateUser) {
    this.candidateUser = candidateUser;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public TaskSearchRequest setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public TaskSearchRequest setProcessInstanceKey(String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public int getPageSize() {
    return pageSize;
  }

  public TaskSearchRequest setPageSize(int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public DateFilterDTO getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchRequest setFollowUpDate(DateFilterDTO followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilterDTO getDueDate() {
    return dueDate;
  }

  public TaskSearchRequest setDueDate(DateFilterDTO dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderByDTO[] getSort() {
    return sort;
  }

  public TaskSearchRequest setSort(TaskOrderByDTO[] sort) {
    this.sort = sort;
    return this;
  }

  public String[] getSearchAfter() {
    return searchAfter;
  }

  public TaskSearchRequest setSearchAfter(String[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public String[] getSearchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public TaskSearchRequest setSearchAfterOrEqual(String[] searchAfterOrEqual) {
    this.searchAfterOrEqual = searchAfterOrEqual;
    return this;
  }

  public String[] getSearchBefore() {
    return searchBefore;
  }

  public TaskSearchRequest setSearchBefore(String[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  public String[] getSearchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public TaskSearchRequest setSearchBeforeOrEqual(String[] searchBeforeOrEqual) {
    this.searchBeforeOrEqual = searchBeforeOrEqual;
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
    final TaskSearchRequest that = (TaskSearchRequest) o;
    return pageSize == that.pageSize
        && state == that.state
        && Objects.equals(assigned, that.assigned)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(taskDefinitionId, that.taskDefinitionId)
        && Objects.equals(candidateGroup, that.candidateGroup)
        && Objects.equals(candidateUser, that.candidateUser)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(sort, that.sort)
        && Arrays.equals(searchAfter, that.searchAfter)
        && Arrays.equals(searchAfterOrEqual, that.searchAfterOrEqual)
        && Arrays.equals(searchBefore, that.searchBefore)
        && Arrays.equals(searchBeforeOrEqual, that.searchBeforeOrEqual);
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
            dueDate);
    result = 31 * result + Arrays.hashCode(sort);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + Arrays.hashCode(searchAfterOrEqual);
    result = 31 * result + Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchBeforeOrEqual);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchRequest.class.getSimpleName() + "[", "]")
        .add("state=" + state)
        .add("assigned=" + assigned)
        .add("assignee='" + assignee + "'")
        .add("taskDefinitionId='" + taskDefinitionId + "'")
        .add("candidateGroup='" + candidateGroup + "'")
        .add("candidateUser='" + candidateUser + "'")
        .add("processDefinitionKey='" + processDefinitionKey + "'")
        .add("processInstanceKey='" + processInstanceKey + "'")
        .add("pageSize=" + pageSize)
        .add("followUpDate=" + followUpDate)
        .add("dueDate=" + dueDate)
        .add("sort=" + Arrays.toString(sort))
        .add("searchAfter=" + Arrays.toString(searchAfter))
        .add("searchAfterOrEqual=" + Arrays.toString(searchAfterOrEqual))
        .add("searchBefore=" + Arrays.toString(searchBefore))
        .add("searchBeforeOrEqual=" + Arrays.toString(searchBeforeOrEqual))
        .toString();
  }
}
