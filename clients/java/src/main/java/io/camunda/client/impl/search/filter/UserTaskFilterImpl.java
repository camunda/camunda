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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.filter.builder.UserTaskStateProperty;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.filter.builder.UserTaskStatePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UserTaskFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.UserTaskFilter>
    implements UserTaskFilter {

  private final io.camunda.client.protocol.rest.UserTaskFilter filter;

  public UserTaskFilterImpl() {
    filter = new io.camunda.client.protocol.rest.UserTaskFilter();
  }

  @Override
  public UserTaskFilter userTaskKey(final Long value) {
    filter.setUserTaskKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public UserTaskFilter state(final UserTaskState state) {
    return state(b -> b.eq(state));
  }

  @Override
  public UserTaskFilter state(final Consumer<UserTaskStateProperty> fn) {
    final UserTaskStateProperty property = new UserTaskStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
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
    filter.setAssignee(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter priority(final Integer priority) {
    priority(b -> b.eq(priority));
    return this;
  }

  @Override
  public UserTaskFilter priority(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setPriority(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter elementId(final String elementId) {
    filter.setElementId(elementId);
    return this;
  }

  @Override
  public UserTaskFilter name(final String name) {
    filter.setName(name);
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
    filter.setCandidateGroup(provideSearchRequestProperty(property));
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
    filter.setCandidateUser(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter processDefinitionKey(final Long processDefinitionKey) {
    filter.setProcessDefinitionKey(ParseUtil.keyToString(processDefinitionKey));
    return this;
  }

  @Override
  public UserTaskFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(processInstanceKey));
    return this;
  }

  @Override
  public UserTaskFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public UserTaskFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter bpmnProcessId(final String bpmnProcessId) {
    filter.processDefinitionId(bpmnProcessId);
    return this;
  }

  @Override
  public UserTaskFilter processInstanceVariables(
      final List<Consumer<VariableValueFilter>> variableFilters) {

    filter.setProcessInstanceVariables(
        VariableFilterMapper.toVariableValueFilterProperty(variableFilters));
    return this;
  }

  @Override
  public UserTaskFilter processInstanceVariables(final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      filter.setProcessInstanceVariables(
          VariableFilterMapper.toVariableValueFilterProperty(variableValueFilters));
    }
    return this;
  }

  @Override
  public UserTaskFilter localVariables(final List<Consumer<VariableValueFilter>> variableFilters) {
    filter.setLocalVariables(VariableFilterMapper.toVariableValueFilterProperty(variableFilters));
    return this;
  }

  @Override
  public UserTaskFilter localVariables(final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      filter.setLocalVariables(
          VariableFilterMapper.toVariableValueFilterProperty(variableValueFilters));
    }
    return this;
  }

  // elementInstanceKey
  @Override
  public UserTaskFilter elementInstanceKey(final Long elementInstanceKey) {
    filter.setElementInstanceKey(ParseUtil.keyToString(elementInstanceKey));
    return this;
  }

  @Override
  public UserTaskFilter creationDate(final OffsetDateTime creationDate) {
    creationDate(b -> b.eq(creationDate));
    return this;
  }

  @Override
  public UserTaskFilter creationDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter completionDate(final OffsetDateTime completionDate) {
    completionDate(b -> b.eq(completionDate));
    return this;
  }

  @Override
  public UserTaskFilter completionDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCompletionDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter followUpDate(final OffsetDateTime followUpDate) {
    followUpDate(b -> b.eq(followUpDate));
    return this;
  }

  @Override
  public UserTaskFilter followUpDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setFollowUpDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public UserTaskFilter dueDate(final OffsetDateTime dueDate) {
    dueDate(b -> b.eq(dueDate));
    return this;
  }

  @Override
  public UserTaskFilter dueDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setDueDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.UserTaskFilter getSearchRequestProperty() {
    return filter;
  }
}
