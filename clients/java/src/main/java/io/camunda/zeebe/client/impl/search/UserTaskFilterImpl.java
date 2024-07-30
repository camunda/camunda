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
package io.camunda.zeebe.client.impl.search;

import io.camunda.zeebe.client.api.search.UserTaskFilter;
import io.camunda.zeebe.client.protocol.rest.DateFilter;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;

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
  public UserTaskFilter key(final Long value) {
    filter.setUserTaskKey(value);
    return this;
  }

  @Override
  public UserTaskFilter state(final String state) {
    filter.setTaskState(state);
    return this;
  }

  @Override
  public UserTaskFilter assignee(final String assignee) {
    filter.setAssignee(assignee);
    return this;
  }

  @Override
  public UserTaskFilter elementId(final String elementId) {
    filter.setElementId(elementId);
    return this;
  }

  @Override
  public UserTaskFilter candidateGroup(final String candidateGroup) {
    filter.setCandidateGroup(candidateGroup);
    return this;
  }

  @Override
  public UserTaskFilter candidateUser(final String candidateUser) {
    filter.setCandidateUser(candidateUser);
    return this;
  }

  @Override
  public UserTaskFilter processDefinitionKey(final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  @Override
  public UserTaskFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  @Override
  public UserTaskFilter followUpDate(final DateFilter dateFilter) {
    filter.setFollowUpDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter dueDate(final DateFilter dateFilter) {
    filter.setDueDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter creationDate(final DateFilter dateFilter) {
    filter.setCreationDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter completionDate(final DateFilter dateFilter) {
    filter.setCompletionDate(dateFilter);
    return this;
  }

  @Override
  public UserTaskFilter tentantId(final String tenantId) {
    filter.setTenantIds(tenantId);
    return this;
  }

  @Override
  public UserTaskFilter bpmProcessId(final String bpmnProcessId) {
    filter.setBpmnProcessDefinitionId(bpmnProcessId);
    return this;
  }

  @Override
  protected UserTaskFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
