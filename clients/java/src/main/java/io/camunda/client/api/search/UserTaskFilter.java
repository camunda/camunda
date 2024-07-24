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
package io.camunda.client.api.search;

import io.camunda.client.api.search.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.client.protocol.rest.DateFilter;

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
  UserTaskFilter userTaskState(final String state);

  /**
   * Filters user tasks by the specified assignee.
   *
   * @param assignee the assignee of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskAssignee(final String assignee);

  /**
   * Filters user tasks by the specified task definition ID.
   *
   * @param taskDefinitionId the task definition ID of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskTaskDefinitionId(final String taskDefinitionId);

  /**
   * Filters user tasks by the specified candidate group.
   *
   * @param candidateGroup the candidate group of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskCandidateGroup(final String candidateGroup);

  /**
   * Filters user tasks by the specified candidate user.
   *
   * @param candidateUser the candidate user of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskCandidateUser(final String candidateUser);

  /**
   * Filters user tasks by the specified process definition key.
   *
   * @param processDefinitionKey the process definition key of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskProcessDefinitionKey(final Long processDefinitionKey);

  /**
   * Filters user tasks by the specified process instance key.
   *
   * @param processInstanceKey the process instance key of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskProcessInstanceKey(final Long processInstanceKey);

  /**
   * Filters user tasks by the specified follow-up date.
   *
   * @param dateFilter the follow-up date filter
   * @return the updated filter
   */
  UserTaskFilter userTaskFollowUpDate(final DateFilter dateFilter);

  /**
   * Filters user tasks by the specified due date.
   *
   * @param dateFilter the due date filter
   * @return the updated filter
   */
  UserTaskFilter userTaskDueDate(final DateFilter dateFilter);

  /**
   * Filters user tasks by the specified creation date.
   *
   * @param dateFilter the creation date filter
   * @return the updated filter
   */
  UserTaskFilter userTaskCreationDate(final DateFilter dateFilter);

  /**
   * Filters user tasks by the specified completion date.
   *
   * @param dateFilter the completion date filter
   * @return the updated filter
   */
  UserTaskFilter userTaskCompletionDate(final DateFilter dateFilter);

  /**
   * Filters user tasks by the specified tenant ID.
   *
   * @param tenantId the tenant ID of the user task
   * @return the updated filter
   */
  UserTaskFilter userTaskTenantId(final String tenantId);
}
