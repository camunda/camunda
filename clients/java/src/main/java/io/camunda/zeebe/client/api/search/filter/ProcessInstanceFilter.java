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

public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by processInstanceKey */
  ProcessInstanceFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processDefinitionId */
  ProcessInstanceFilter processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionName */
  ProcessInstanceFilter processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersionTag */
  ProcessInstanceFilter processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionKey */
  ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey);

  /** Filter by rootProcessInstanceKey */
  ProcessInstanceFilter rootProcessInstanceKey(final Long rootProcessInstanceKey);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentFlowNodeInstanceKey */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey);

  /** Filter by treePath */
  ProcessInstanceFilter treePath(final String treePath);

  /** Filter by startDate */
  ProcessInstanceFilter startDate(final String startDate);

  /** Filter by endDate */
  ProcessInstanceFilter endDate(final String endDate);

  /** Filter by state */
  ProcessInstanceFilter state(final String state);

  /** Filter by hasIncident */
  ProcessInstanceFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessInstanceFilter tenantId(final String tenantId);
}
