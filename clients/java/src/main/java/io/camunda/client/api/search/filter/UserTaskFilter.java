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
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.filter.builder.UserTaskStateProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Interface for defining user task filters in search queries. */
public interface UserTaskFilter extends SearchRequestFilter {

  /**
   * Filters user tasks by the specified key.
   *
   * @param value the key of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskKey(final Long value);

  /**
   * Filters user tasks by the specified state.
   *
   * @param state the state of the user task
   * @return the updated filter
   */
  UserTaskFilter state(final UserTaskState state);

  /**
   * Filters user tasks by the specified state using {@link UserTaskStateProperty} consumer.
   *
   * @param fn the {@link UserTaskStateProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter state(final Consumer<UserTaskStateProperty> fn);

  /**
   * Filters user tasks by the specified assignee.
   *
   * @param assignee the assignee of the user task
   * @return the updated filter
   */
  UserTaskFilter assignee(final String assignee);

  /**
   * Filters user tasks by the specified assignee using {@link StringProperty} consumer.
   *
   * @param fn the assignee {@link StringProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter assignee(final Consumer<StringProperty> fn);

  /**
   * Filters user tasks by the specified priority.
   *
   * @param priority the priority of the user task
   * @return the updated filter
   */
  UserTaskFilter priority(final Integer priority);

  /**
   * Filters user tasks by the specified priority using {@link IntegerProperty} consumer.
   *
   * @param fn the priority {@link IntegerProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter priority(final Consumer<IntegerProperty> fn);

  /**
   * Filters user tasks by the specified task definition ID.
   *
   * @param taskDefinitionId the task definition ID of the user task
   * @return the updated filter
   */
  UserTaskFilter elementId(final String taskDefinitionId);

  /**
   * Filters user tasks by their name. This only works for data created with 8.8 and onwards.
   * Instances from prior versions don't contain this data.
   *
   * @param name the element name of the user task
   * @return the updated filter
   */
  UserTaskFilter name(final String name);

  /**
   * Filters user tasks by the specified candidate group.
   *
   * @param candidateGroup the candidate group of the user task
   * @return the updated filter
   */
  UserTaskFilter candidateGroup(final String candidateGroup);

  /**
   * Filters user tasks by the specified candidate group using {@link StringProperty} consumer.
   *
   * @param fn the candidate group {@link StringProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter candidateGroup(final Consumer<StringProperty> fn);

  /**
   * Filters user tasks by the specified candidate user.
   *
   * @param candidateUser the candidate user of the user task
   * @return the updated filter
   */
  UserTaskFilter candidateUser(final String candidateUser);

  /**
   * Filters user tasks by the specified candidate user using {@link StringProperty} consumer.
   *
   * @param fn the candidate user {@link StringProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter candidateUser(final Consumer<StringProperty> fn);

  /**
   * Filters user tasks by the specified process definition key.
   *
   * @param processDefinitionKey the process definition key of the user task
   * @return the updated filter
   */
  UserTaskFilter processDefinitionKey(final Long processDefinitionKey);

  /**
   * Filters user tasks by the specified process instance key.
   *
   * @param processInstanceKey the process instance key of the user task
   * @return the updated filter
   */
  UserTaskFilter processInstanceKey(final Long processInstanceKey);

  /**
   * Filters user tasks by the specified tenant ID.
   *
   * @param tenantId representing the tenant associated with this task
   * @return the updated filter
   */
  UserTaskFilter tenantId(final String tenantId);

  /**
   * Filters user tasks by the specified tenantId using {@link StringProperty} consumer.
   *
   * @param fn the {@link StringProperty} consumer of the user task
   * @return the updated filter
   */
  UserTaskFilter tenantId(final Consumer<StringProperty> fn);

  /**
   * Filters user tasks by the specified Process Definition Id.
   *
   * @param bpmnProcessId from the task
   * @return the updated filter
   */
  UserTaskFilter bpmnProcessId(final String bpmnProcessId);

  /**
   * Filters user tasks by specified Process Instance Variables.
   *
   * @param variableValueFilters from the task
   * @return the updated filter
   */
  UserTaskFilter processInstanceVariables(
      final List<Consumer<VariableValueFilter>> variableValueFilters);

  /**
   * Filters user tasks by specified Map of Process Instance Variables.
   *
   * @param variableValueFilters from the task
   * @return the updated filter
   */
  UserTaskFilter processInstanceVariables(final Map<String, Object> variableValueFilters);

  /**
   * Filters user tasks by specified Local Variables.
   *
   * @param variableValueFilters from the task
   * @return the updated filter
   */
  UserTaskFilter localVariables(final List<Consumer<VariableValueFilter>> variableValueFilters);

  /**
   * Filters user tasks by specified Map of Local Variables.
   *
   * @param variableValueFilters from the task
   * @return the updated filter
   */
  UserTaskFilter localVariables(final Map<String, Object> variableValueFilters);

  /**
   * Filters user tasks by the specified element instance key.
   *
   * @param elementInstanceKey the element instance key of the user task
   * @return the updated filter
   */
  UserTaskFilter elementInstanceKey(final Long elementInstanceKey);

  /**
   * Filters user tasks by the specified creation date.
   *
   * @param creationDate the creation date of the user task
   * @return the updated filter
   */
  UserTaskFilter creationDate(final OffsetDateTime creationDate);

  /**
   * Filters user tasks by the specified {@link DateTimeProperty} creation date.
   *
   * @param creationDate the creation date of the user task
   * @return the updated filter
   */
  UserTaskFilter creationDate(final Consumer<DateTimeProperty> creationDate);

  /**
   * Filters user tasks by the specified completion date.
   *
   * @param completionDate the completion date of the user task
   * @return the updated filter
   */
  UserTaskFilter completionDate(final OffsetDateTime completionDate);

  /**
   * Filters user tasks by the specified {@link DateTimeProperty} completion date.
   *
   * @param completionDate the completion date of the user task
   * @return the updated filter
   */
  UserTaskFilter completionDate(final Consumer<DateTimeProperty> completionDate);

  /**
   * Filters user tasks by the specified follow-up date.
   *
   * @param followUpDate the follow-up date of the user task
   * @return the updated filter
   */
  UserTaskFilter followUpDate(final OffsetDateTime followUpDate);

  /**
   * Filters user tasks by the specified {@link DateTimeProperty} follow-up date.
   *
   * @param followUpDate the follow-up date of the user task
   * @return the updated filter
   */
  UserTaskFilter followUpDate(final Consumer<DateTimeProperty> followUpDate);

  /**
   * Filters user tasks by the specified due date.
   *
   * @param dueDate the due date of the user task
   * @return the updated filter
   */
  UserTaskFilter dueDate(final OffsetDateTime dueDate);

  /**
   * Filters user tasks by the specified {@link DateTimeProperty} due date.
   *
   * @param dueDate the due date of the user task
   * @return the updated filter
   */
  UserTaskFilter dueDate(final Consumer<DateTimeProperty> dueDate);
}
