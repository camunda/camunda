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
package io.camunda.client.impl.search;

import io.camunda.client.api.search.UserTaskFilter;
import io.camunda.client.protocol.rest.DateFilter;
import io.camunda.client.protocol.rest.UserTaskFilterRequest;

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
  public UserTaskFilter userTaskKey(final Long value) {
    filter.setUserTaskKey(value);
    return this;
  }

  @Override
  public UserTaskFilter userTaskState(final String state) {
    filter.setTaskState(state);
    return this;
  }

  @Override
  public UserTaskFilter userTaskAssignee(final String assignee) {
    filter.setAssignee(assignee);
    return this;
  }

  @Override
  public UserTaskFilter userTaskTaskDefinitionId(final String taskDefinitionId) {
    filter.setElementId(taskDefinitionId);
    return this;
  }

  @Override
  public UserTaskFilter userTaskCandidateGroup(final String candidateGroup) {
    filter.setCandidateGroup(candidateGroup);
    return this;
  }

  @Override
  public UserTaskFilter userTaskCandidateUser(final String candidateUser) {
    filter.setCandidateUser(candidateUser);
    return this;
  }

  @Override
  public UserTaskFilter userTaskProcessDefinitionKey(final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public UserTaskFilter userTaskProcessInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
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
    filter.setCreationDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter userTaskCompletionDate(final DateFilter dateFilter) {
    filter.setCompletionDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter userTaskTenantId(final String tenantId) {
    filter.setTenantIds(tenantId);
    return this;
  }

  @Override
  protected UserTaskFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
