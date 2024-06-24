/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.search.UserTaskFilter;
import io.camunda.zeebe.client.protocol.rest.DateFilter;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;
import java.util.List;

public class UserTaskFilterImpl extends TypedSearchRequestPropertyProvider<UserTaskFilterRequest>
    implements UserTaskFilter {

  private final UserTaskFilterRequest filter;

  public UserTaskFilterImpl(final UserTaskFilterRequest filter) {
    this.filter = new UserTaskFilterRequest();
  }

  public UserTaskFilterImpl() {
    filter = new UserTaskFilterRequest();
  }

  @Override
  public UserTaskFilter userTaskKeys(final Long value, final Long... values) {
    return userTaskKeys(UserTaskFilter.collectValues(value, values));
  }

  @Override
  public UserTaskFilter userTaskKeys(final List<Long> values) {
    filter.setKey(UserTaskFilter.addValuesToList(filter.getKey(), values));
    return this;
  }

  @Override
  public UserTaskFilter userTaskStates(final String state, final String... states) {
    return userTaskStates(UserTaskFilter.collectValues(state, states));
  }

  @Override
  public UserTaskFilter userTaskStates(final List<String> states) {
    filter.setTaskState(UserTaskFilter.addValuesToList(filter.getTaskState(), states));
    return this;
  }

  @Override
  public UserTaskFilter userTaskAssignees(final String assignee, final String... assignees) {
    return userTaskAssignees(UserTaskFilter.collectValues(assignee, assignees));
  }

  @Override
  public UserTaskFilter userTaskAssignees(final List<String> assignees) {
    filter.setAssignee(UserTaskFilter.addValuesToList(filter.getAssignee(), assignees));
    return this;
  }

  @Override
  public UserTaskFilter userTaskTaskDefinitionIds(
      final String taskDefinitionId, final String... taskDefinitionIds) {
    return userTaskTaskDefinitionIds(
        UserTaskFilter.collectValues(taskDefinitionId, taskDefinitionIds));
  }

  @Override
  public UserTaskFilter userTaskTaskDefinitionIds(final List<String> taskDefinitionIds) {
    filter.setTaskDefinitionId(
        UserTaskFilter.addValuesToList(filter.getTaskDefinitionId(), taskDefinitionIds));
    return this;
  }

  @Override
  public UserTaskFilter userTaskCandidateGroups(
      final String candidateGroup, final String... candidateGroups) {
    return userTaskCandidateGroups(UserTaskFilter.collectValues(candidateGroup, candidateGroups));
  }

  @Override
  public UserTaskFilter userTaskCandidateGroups(final List<String> candidateGroups) {
    filter.setCandidateGroup(
        UserTaskFilter.addValuesToList(filter.getCandidateGroup(), candidateGroups));
    return this;
  }

  @Override
  public UserTaskFilter userTaskCandidateUsers(
      final String candidateUser, final String... candidateUsers) {
    return userTaskCandidateUsers(UserTaskFilter.collectValues(candidateUser, candidateUsers));
  }

  @Override
  public UserTaskFilter userTaskCandidateUsers(final List<String> candidateUsers) {
    filter.setCandidateUser(
        UserTaskFilter.addValuesToList(filter.getCandidateUser(), candidateUsers));
    return this;
  }

  @Override
  public UserTaskFilter userTaskProcessDefinitionKeys(
      final Long processDefinitionKey, final Long... processDefinitionKeys) {
    return userTaskProcessDefinitionKeys(
        UserTaskFilter.collectValues(processDefinitionKey, processDefinitionKeys));
  }

  @Override
  public UserTaskFilter userTaskProcessDefinitionKeys(final List<Long> processDefinitionKeys) {
    filter.setProcessDefinitionKey(
        UserTaskFilter.addValuesToList(filter.getProcessDefinitionKey(), processDefinitionKeys));
    return this;
  }

  @Override
  public UserTaskFilter userTaskProcessInstanceKeys(
      final Long processInstanceKey, final Long... processInstanceKeys) {
    return userTaskProcessInstanceKeys(
        UserTaskFilter.collectValues(processInstanceKey, processInstanceKeys));
  }

  @Override
  public UserTaskFilter userTaskProcessInstanceKeys(final List<Long> processInstanceKeys) {
    filter.setProcessInstanceKey(
        UserTaskFilter.addValuesToList(filter.getProcessInstanceKey(), processInstanceKeys));
    return this;
  }

  @Override
  public UserTaskFilter userTaskFollowUpDate(final DateFilter dateFilter) {
    filter.setFollowUpDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter userTaskDueDate(final DateFilter dateFilter) {
    filter.setDueDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter userTaskCreationDate(final DateFilter dateFilter) {
    return null;
  }

  @Override
  public UserTaskFilter userTaskCompletionDate(final DateFilter dateFilter) {
    filter.setCompletionTime(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter userTaskTenantIds(final String tenantId, final String... tenantIds) {
    return userTaskTenantIds(UserTaskFilter.collectValues(tenantId, tenantIds));
  }

  @Override
  public UserTaskFilter userTaskTenantIds(final List<String> tenantIds) {
    filter.setTenantIds(UserTaskFilter.addValuesToList(filter.getTenantIds(), tenantIds));
    return this;
  }

  @Override
  public UserTaskFilter userTaskCreatedDate(final DateFilter dateFilter) {
    filter.setCreationTime(dateFilter);
    return this;
  }

  @Override
  protected UserTaskFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
