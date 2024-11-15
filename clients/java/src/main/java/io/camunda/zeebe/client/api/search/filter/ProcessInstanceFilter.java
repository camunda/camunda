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

import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.DateTimePropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.IntegerPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.LongPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.ProcessInstanceStatePropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.StringPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;
import java.time.OffsetDateTime;

public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by processInstanceKey */
  ProcessInstanceFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link LongPropertyBuilderCallback} */
  ProcessInstanceFilter processInstanceKey(final LongPropertyBuilderCallback callback);

  /** Filter by processDefinitionId */
  ProcessInstanceFilter processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionId using {@link StringPropertyBuilderCallback} */
  ProcessInstanceFilter processDefinitionId(final StringPropertyBuilderCallback callback);

  /** Filter by processDefinitionName */
  ProcessInstanceFilter processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionName using {@link StringPropertyBuilderCallback} */
  ProcessInstanceFilter processDefinitionName(final StringPropertyBuilderCallback callback);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersion using {@link IntegerPropertyBuilderCallback} */
  ProcessInstanceFilter processDefinitionVersion(final IntegerPropertyBuilderCallback callback);

  /** Filter by processDefinitionVersionTag */
  ProcessInstanceFilter processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionVersionTag using {@link StringPropertyBuilderCallback} */
  ProcessInstanceFilter processDefinitionVersionTag(final StringPropertyBuilderCallback callback);

  /** Filter by processDefinitionKey */
  ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey);

  /** Filter by processDefinitionKey using {@link LongPropertyBuilderCallback} */
  ProcessInstanceFilter processDefinitionKey(final LongPropertyBuilderCallback callback);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link LongPropertyBuilderCallback} */
  ProcessInstanceFilter parentProcessInstanceKey(final LongPropertyBuilderCallback callback);

  /** Filter by parentFlowNodeInstanceKey */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey);

  /** Filter by parentFlowNodeInstanceKey using {@link LongPropertyBuilderCallback} */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final LongPropertyBuilderCallback callback);

  /** Filter by treePath */
  ProcessInstanceFilter treePath(final String treePath);

  /** Filter by treePath using {@link StringPropertyBuilderCallback} */
  ProcessInstanceFilter treePath(final StringPropertyBuilderCallback callback);

  /** Filter by startDate */
  ProcessInstanceFilter startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimePropertyBuilderCallback} */
  ProcessInstanceFilter startDate(final DateTimePropertyBuilderCallback callback);

  /** Filter by endDate */
  ProcessInstanceFilter endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimePropertyBuilderCallback} */
  ProcessInstanceFilter endDate(final DateTimePropertyBuilderCallback callback);

  /** Filter by state */
  ProcessInstanceFilter state(final String state);

  /** Filter by state using {@link ProcessInstanceStatePropertyBuilderCallback} */
  ProcessInstanceFilter state(final ProcessInstanceStatePropertyBuilderCallback callback);

  /** Filter by hasIncident */
  ProcessInstanceFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessInstanceFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringPropertyBuilderCallback} */
  ProcessInstanceFilter tenantId(final StringPropertyBuilderCallback callback);
}
