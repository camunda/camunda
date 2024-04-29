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
package io.camunda.tasklist.webapp.graphql.entity;

import graphql.annotations.annotationTypes.GraphQLConstructor;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLType;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.queries.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@GraphQLType
@GraphQLName("TaskQuery")
public class TaskQueryDTO {

  public static final int DEFAULT_PAGE_SIZE = 50;
  @GraphQLField private TaskState state;
  @GraphQLField private Boolean assigned;
  @GraphQLField private String assignee;
  private String[] assignees;
  @GraphQLField private String taskDefinitionId;
  @GraphQLField private String candidateGroup;
  private String[] candidateGroups;
  @GraphQLField private String candidateUser;
  private String[] candidateUsers;
  @GraphQLField private String processDefinitionId;
  @GraphQLField private String processInstanceId;
  private TaskByVariables[] taskVariables;
  private String[] tenantIds;
  @GraphQLField private int pageSize = DEFAULT_PAGE_SIZE;
  @GraphQLField private String[] searchAfter;
  @GraphQLField private String[] searchAfterOrEqual;
  @GraphQLField private String[] searchBefore;
  @GraphQLField private String[] searchBeforeOrEqual;
  @GraphQLField private DateFilter followUpDate;
  @GraphQLField private DateFilter dueDate;
  @GraphQLField private TaskOrderBy[] sort;
  private TaskByCandidateUserOrGroup taskByCandidateUserOrGroup;
  @GraphQLField private TaskImplementation implementation;

  // Constructor used by GraphQL, it should initialize fields annotated with @GraphQLField
  @GraphQLConstructor
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
  }

  public TaskQueryDTO() {}

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
