/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.entities.TaskState;

public class TaskQueryDTO {

  private static final int DEFAULT_PAGE_SIZE = 50;

  private TaskState state;
  private Boolean assigned;
  private String assignee;
  private String taskDefinitionId;
  private String candidateGroup;
  private String candidateUser;
  private String processDefinitionId;
  private String processInstanceId;
  private int pageSize = DEFAULT_PAGE_SIZE;
  private String[] searchAfter;
  private String[] searchAfterOrEqual;
  private String[] searchBefore;
  private String[] searchBeforeOrEqual;
  private DateFilterDTO followUpDate;
  private DateFilterDTO dueDate;
  private TaskOrderByDTO[] sort;

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

  public TaskQueryDTO createCopy() {
    return new TaskQueryDTO()
        .setAssigned(this.assigned)
        .setAssignee(this.assignee)
        .setTaskDefinitionId(this.taskDefinitionId)
        .setPageSize(this.pageSize)
        .setSearchAfter(this.searchAfter)
        .setSearchAfterOrEqual(this.searchAfterOrEqual)
        .setSearchBefore(this.searchBefore)
        .setSearchBeforeOrEqual(this.searchBeforeOrEqual)
        .setState(this.state)
        .setCandidateGroup(this.candidateGroup);
  }

  public DateFilterDTO getFollowUpDate() {
    return followUpDate;
  }

  public TaskQueryDTO setFollowUpDate(DateFilterDTO followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public DateFilterDTO getDueDate() {
    return dueDate;
  }

  public TaskQueryDTO setDueDate(DateFilterDTO dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public TaskOrderByDTO[] getSort() {
    return sort;
  }

  public TaskQueryDTO setSort(TaskOrderByDTO[] sort) {
    this.sort = sort;
    return this;
  }
}
