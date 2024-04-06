/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.queries;

import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
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

  public String[] getAssignees() {
    return assignees;
  }

  public TaskQuery setAssignees(String[] assignees) {
    this.assignees = assignees;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskQuery setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskQuery setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
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

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskQuery setImplementation(TaskImplementation implementation) {
    this.implementation = implementation;
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
        .setTaskByCandidateUserOrGroups(this.taskByCandidateUserOrGroups)
        .setImplementation(this.implementation)
        .setAssignees(this.assignees)
        .setCandidateGroups(this.candidateGroups)
        .setCandidateUsers(this.candidateUsers);
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
        && Arrays.equals(sort, taskQuery.sort)
        && Objects.equals(taskByCandidateUserOrGroups, taskQuery.taskByCandidateUserOrGroups);
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
            implementation);
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
        + '}';
  }
}
