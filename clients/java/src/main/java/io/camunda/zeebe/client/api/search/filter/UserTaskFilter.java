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
package io.camunda.zeebe.client.api.search.filter;

import io.camunda.zeebe.client.api.search.filter.builder.IntegerProperty;
import io.camunda.zeebe.client.api.search.filter.builder.StringProperty;
import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.protocol.rest.UserTaskVariableFilterRequest;
import java.util.List;
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
  UserTaskFilter state(final String state);

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
   * @param tenantId the tenant ID of the user task
   * @return the updated filter
   */
  UserTaskFilter tenantId(final String tenantId);

  /**
   * Filters user tasks by the specified Process Definition Id.
   *
   * @param bpmnProcessId from the task
   * @return the updated filter
   */
  UserTaskFilter bpmnProcessId(final String bpmnProcessId);

  /**
   * Filters user tasks by the specified Process Definition Id.
   *
   * @param variableValueFilters from the task
   * @return the updated filter
   */
  UserTaskFilter variables(final List<UserTaskVariableFilterRequest> variableValueFilters);
}
