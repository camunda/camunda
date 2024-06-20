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
package io.camunda.zeebe.client.api.search;

import io.camunda.zeebe.client.api.search.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.protocol.rest.DateFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public interface UserTaskFilter extends SearchRequestFilter {

  UserTaskFilter userTaskKeys(final Long value, final Long... values);

  UserTaskFilter userTaskKeys(final List<Long> values);

  UserTaskFilter userTaskStates(final String state, final String... states);

  UserTaskFilter userTaskStates(final List<String> states);

  UserTaskFilter userTaskAssignees(final String assignee, final String... assignees);

  UserTaskFilter userTaskAssignees(final List<String> assignees);

  UserTaskFilter userTaskTaskDefinitionIds(final String taskDefinitionId, final String... taskDefinitionIds);

  UserTaskFilter userTaskTaskDefinitionIds(final List<String> taskDefinitionIds);

  UserTaskFilter userTaskCandidateGroups(final String candidateGroup, final String... candidateGroups);

  UserTaskFilter userTaskCandidateGroups(final List<String> candidateGroups);

  UserTaskFilter userTaskCandidateUsers(final String candidateUser, final String... candidateUsers);

  UserTaskFilter userTaskCandidateUsers(final List<String> candidateUsers);

  UserTaskFilter userTaskProcessDefinitionKeys(final Long processDefinitionKey, final Long... processDefinitionKeys);

  UserTaskFilter userTaskProcessDefinitionKeys(final List<Long> processDefinitionKeys);

  UserTaskFilter userTaskProcessInstanceKeys(final Long processInstanceKey, final Long... processInstanceKeys);

  UserTaskFilter userTaskProcessInstanceKeys(final List<Long> processInstanceKeys);

  UserTaskFilter userTaskFollowUpDate(final DateFilter dateFilter);

  UserTaskFilter userTaskTenantIds(final String tenantId, final String... tenantIds);

  UserTaskFilter userTaskTenantIds(final List<String> tenantIds);

  UserTaskFilter userTaskCreatedDate(final DateFilter dateFilter);

  // TODO move this to a shared utility module

  public static <T> List<T> addValuesToList(final List<T> list, final List<T> values) {
    final List<T> result;
    if (list == null) {
      result = Objects.requireNonNull(values);
    } else {
      result = new ArrayList<>(list);
      result.addAll(values);
    }
    return result;
  }

  public static <T> List<T> collectValues(final T value, final T... values) {
    final List<T> collectedValues = new ArrayList<>();
    collectedValues.add(value);
    if (values != null && values.length > 0) {
      collectedValues.addAll(Arrays.asList(values));
    }
    return collectedValues;
  }
}
