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

import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.response.UserTaskState;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.UserTaskVariableFilterRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    filter.setState(UserTaskState.toProtocolState(state));
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
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  public UserTaskFilter bpmnProcessId(final String bpmnProcessId) {
    filter.processDefinitionId(bpmnProcessId);
    return this;
  }

  @Override
  public UserTaskFilter processInstanceVariables(
      final List<UserTaskVariableFilterRequest> variableValueFilters) {
    if (variableValueFilters != null) {
      variableValueFilters.forEach(v -> variableValueNullCheck(v.getValue()));
    }
    filter.setProcessInstanceVariables(variableValueFilters);
    return this;
  }

  @Override
  public UserTaskFilter processInstanceVariables(final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      final List<UserTaskVariableFilterRequest> variableFilters =
          variableValueFilters.entrySet().stream()
              .map(
                  entry -> {
                    variableValueNullCheck(entry.getValue());
                    final UserTaskVariableFilterRequest request =
                        new UserTaskVariableFilterRequest();
                    request.setName(entry.getKey());
                    final StringProperty property = new StringPropertyImpl();
                    property.eq(entry.getValue().toString());
                    request.setValue(property.build());
                    return request;
                  })
              .collect(Collectors.toList());
      filter.setProcessInstanceVariables(variableFilters);
    }
    return this;
  }

  @Override
  public UserTaskFilter localVariables(
      final List<UserTaskVariableFilterRequest> variableValueFilters) {
    if (variableValueFilters != null) {
      variableValueFilters.forEach(v -> variableValueNullCheck(v.getValue()));
    }
    filter.setLocalVariables(variableValueFilters);
    return this;
  }

  @Override
  public UserTaskFilter localVariables(final Map<String, Object> variableValueFilters) {
    if (variableValueFilters != null && !variableValueFilters.isEmpty()) {
      final List<UserTaskVariableFilterRequest> variableFilters =
          variableValueFilters.entrySet().stream()
              .map(
                  entry -> {
                    variableValueNullCheck(entry.getValue());
                    final UserTaskVariableFilterRequest request =
                        new UserTaskVariableFilterRequest();
                    request.setName(entry.getKey());
                    final StringProperty property = new StringPropertyImpl();
                    property.eq(entry.getValue().toString());
                    request.setValue(property.build());
                    return request;
                  })
              .collect(Collectors.toList());
      filter.setLocalVariables(variableFilters);
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
    filter.setCreationDate(property.build());
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
    filter.setCompletionDate(property.build());
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
    filter.setFollowUpDate(property.build());
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
    filter.setDueDate(property.build());
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.UserTaskFilter getSearchRequestProperty() {
    return filter;
  }

  static void variableValueNullCheck(final Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Variable value cannot be null");
    }
  }
}
