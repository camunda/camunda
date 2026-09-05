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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.filter.builder.UserTaskStateProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Interface for defining user task filters in search queries. */
public interface UserTaskFilter extends UserTaskFilterBase {

  @Override
  UserTaskFilter userTaskKey(final Long value);

  @Override
  UserTaskFilter state(final UserTaskState state);

  @Override
  UserTaskFilter state(final Consumer<UserTaskStateProperty> fn);

  @Override
  UserTaskFilter assignee(final String assignee);

  @Override
  UserTaskFilter assignee(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter businessId(final String businessId);

  @Override
  UserTaskFilter businessId(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter priority(final Integer priority);

  @Override
  UserTaskFilter priority(final Consumer<IntegerProperty> fn);

  @Override
  UserTaskFilter elementId(final String taskDefinitionId);

  @Override
  UserTaskFilter name(final String name);

  @Override
  UserTaskFilter name(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter tags(final Set<String> tags);

  @Override
  UserTaskFilter tags(final String... tags);

  @Override
  UserTaskFilter candidateGroup(final String candidateGroup);

  @Override
  UserTaskFilter candidateGroup(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter candidateUser(final String candidateUser);

  @Override
  UserTaskFilter candidateUser(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter processDefinitionKey(final Long processDefinitionKey);

  @Override
  UserTaskFilter processDefinitionKey(final Consumer<BasicLongProperty> fn);

  @Override
  UserTaskFilter processInstanceKey(final Long processInstanceKey);

  @Override
  UserTaskFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  @Override
  UserTaskFilter tenantId(final String tenantId);

  @Override
  UserTaskFilter tenantId(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter bpmnProcessId(final String bpmnProcessId);

  @Override
  UserTaskFilter bpmnProcessId(final Consumer<StringProperty> fn);

  @Override
  UserTaskFilter processInstanceVariables(
      final List<Consumer<VariableValueFilter>> variableValueFilters);

  @Override
  UserTaskFilter processInstanceVariables(final Map<String, Object> variableValueFilters);

  @Override
  UserTaskFilter localVariables(final List<Consumer<VariableValueFilter>> variableValueFilters);

  @Override
  UserTaskFilter localVariables(final Map<String, Object> variableValueFilters);

  @Override
  UserTaskFilter elementInstanceKey(final Long elementInstanceKey);

  @Override
  UserTaskFilter creationDate(final OffsetDateTime creationDate);

  @Override
  UserTaskFilter creationDate(final Consumer<DateTimeProperty> creationDate);

  @Override
  UserTaskFilter completionDate(final OffsetDateTime completionDate);

  @Override
  UserTaskFilter completionDate(final Consumer<DateTimeProperty> completionDate);

  @Override
  UserTaskFilter followUpDate(final OffsetDateTime followUpDate);

  @Override
  UserTaskFilter followUpDate(final Consumer<DateTimeProperty> followUpDate);

  @Override
  UserTaskFilter dueDate(final OffsetDateTime dueDate);

  @Override
  UserTaskFilter dueDate(final Consumer<DateTimeProperty> dueDate);

  /** Filter by or conjunction using {@link UserTaskFilterBase} consumer. */
  UserTaskFilterBase orFilters(List<Consumer<UserTaskFilterBase>> filters);
}
