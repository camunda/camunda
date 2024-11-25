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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.UserTaskFilter;
import io.camunda.zeebe.client.api.search.filter.builder.IntegerProperty;
import io.camunda.zeebe.client.api.search.filter.builder.StringProperty;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskVariableFilterRequest;
import java.util.List;
import java.util.function.Consumer;

public class UserTaskFilterImpl extends TypedSearchRequestPropertyProvider<UserTaskFilterRequest>
    implements UserTaskFilter {

  private final UserTaskFilterRequest filter;

  public UserTaskFilterImpl() {
    filter = new UserTaskFilterRequest();
  }

  @Override
  public UserTaskFilter userTaskKey(final Long value) {
    filter.setUserTaskKey(value);
    return this;
  }

  @Override
  public UserTaskFilter state(final String state) {
    filter.setState((state == null) ? null : UserTaskFilterRequest.StateEnum.fromValue(state));
    return this;
  }

  @Override
  public UserTaskFilter assignee(final String assignee) {
    assignee(b -> b.eq(assignee));
    return this;
  }

  @Override
  public UserTaskFilter assignee(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setAssignee(property.build());
    return this;
  }

  @Override
  public UserTaskFilter priority(final Integer priority) {
    priority(b -> b.eq(priority));
    return this;
  }

  @Override
  public UserTaskFilter priority(final Consumer<IntegerProperty> fn) {
    final IntegerPropertyImpl property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setPriority(property.build());
    return this;
  }

  @Override
  public UserTaskFilter elementId(final String elementId) {
    filter.setElementId(elementId);
    return this;
  }

  @Override
  public UserTaskFilter candidateGroup(final String candidateGroup) {
    candidateGroup(b -> b.eq(candidateGroup));
    return this;
  }

  @Override
  public UserTaskFilter candidateGroup(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setCandidateGroup(property.build());
    return this;
  }

  @Override
  public UserTaskFilter candidateUser(final String candidateUser) {
    candidateUser(b -> b.eq(candidateUser));
    return this;
  }

  @Override
  public UserTaskFilter candidateUser(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setCandidateUser(property.build());
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
  public UserTaskFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  public UserTaskFilter bpmnProcessId(final String bpmnProcessId) {
    filter.processDefinitionId(bpmnProcessId);
    return this;
  }

  @Override
  public UserTaskFilter variables(final List<UserTaskVariableFilterRequest> variableValueFilters) {
    filter.setVariables(variableValueFilters);
    return this;
  }

  // elementInstanceKey
  @Override
  public UserTaskFilter elementInstanceKey(final Long elementInstanceKey) {
    filter.setElementInstanceKey(elementInstanceKey);
    return this;
  }

  @Override
  protected UserTaskFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
