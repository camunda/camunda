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

import io.camunda.zeebe.client.api.search.query.TypedSearchQueryRequest.SearchRequestFilter;
import io.camunda.zeebe.client.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceStateFilterProperty;
import io.camunda.zeebe.client.protocol.rest.StringFilterProperty;

public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by processInstanceKey */
  ProcessInstanceFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link LongFilterProperty} */
  ProcessInstanceFilter processInstanceKey(final LongFilterProperty processInstanceKeyFilter);

  /** Filter by processDefinitionId */
  ProcessInstanceFilter processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionId using {@link StringFilterProperty} */
  ProcessInstanceFilter processDefinitionId(final StringFilterProperty processDefinitionId);

  /** Filter by processDefinitionName */
  ProcessInstanceFilter processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionName using {@link StringFilterProperty} */
  ProcessInstanceFilter processDefinitionName(final StringFilterProperty processDefinitionName);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersion using {@link IntegerFilterProperty} */
  ProcessInstanceFilter processDefinitionVersion(
      final IntegerFilterProperty processDefinitionVersion);

  /** Filter by processDefinitionVersionTag */
  ProcessInstanceFilter processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionVersionTag using {@link StringFilterProperty} */
  ProcessInstanceFilter processDefinitionVersionTag(
      final StringFilterProperty processDefinitionVersionTag);

  /** Filter by processDefinitionKey */
  ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey);

  /** Filter by processDefinitionKey using {@link LongFilterProperty} */
  ProcessInstanceFilter processDefinitionKey(final LongFilterProperty processDefinitionKey);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link LongFilterProperty} */
  ProcessInstanceFilter parentProcessInstanceKey(final LongFilterProperty parentProcessInstanceKey);

  /** Filter by parentFlowNodeInstanceKey */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey);

  /** Filter by parentFlowNodeInstanceKey using {@link LongFilterProperty} */
  ProcessInstanceFilter parentFlowNodeInstanceKey(
      final LongFilterProperty parentFlowNodeInstanceKey);

  /** Filter by treePath */
  ProcessInstanceFilter treePath(final String treePath);

  /** Filter by treePath using {@link StringFilterProperty} */
  ProcessInstanceFilter treePath(final StringFilterProperty treePath);

  /** Filter by startDate */
  ProcessInstanceFilter startDate(final String startDate);

  /** Filter by endDate */
  ProcessInstanceFilter endDate(final String endDate);

  /** Filter by state */
  ProcessInstanceFilter state(final String state);

  /** Filter by state using {@link ProcessInstanceStateFilterProperty} */
  ProcessInstanceFilter state(final ProcessInstanceStateFilterProperty state);

  /** Filter by hasIncident */
  ProcessInstanceFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessInstanceFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringFilterProperty} */
  ProcessInstanceFilter tenantId(final StringFilterProperty tenantId);
}
