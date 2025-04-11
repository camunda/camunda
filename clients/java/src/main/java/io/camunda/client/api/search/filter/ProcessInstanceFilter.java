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

import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.FlowNodeInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessInstanceFilter extends SearchRequestFilter {

  /** Filter by processInstanceKey */
  ProcessInstanceFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by processDefinitionId */
  ProcessInstanceFilter processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionId using {@link StringProperty} */
  ProcessInstanceFilter processDefinitionId(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionName */
  ProcessInstanceFilter processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionName using {@link StringProperty} consumer */
  ProcessInstanceFilter processDefinitionName(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersion using {@link IntegerProperty} consumer */
  ProcessInstanceFilter processDefinitionVersion(final Consumer<IntegerProperty> fn);

  /** Filter by processDefinitionVersionTag */
  ProcessInstanceFilter processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionVersionTag using {@link StringProperty} consumer */
  ProcessInstanceFilter processDefinitionVersionTag(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionKey */
  ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey);

  /** Filter by processDefinitionKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilter processDefinitionKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilter parentProcessInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentFlowNodeInstanceKey */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final Long parentFlowNodeInstanceKey);

  /** Filter by parentFlowNodeInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilter parentFlowNodeInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  ProcessInstanceFilter startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  ProcessInstanceFilter startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  ProcessInstanceFilter endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  ProcessInstanceFilter endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  ProcessInstanceFilter state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  ProcessInstanceFilter state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  ProcessInstanceFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessInstanceFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  ProcessInstanceFilter tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  ProcessInstanceFilter variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters);

  /** Filter by variables map */
  ProcessInstanceFilter variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  ProcessInstanceFilter batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  ProcessInstanceFilter batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  ProcessInstanceFilter errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  ProcessInstanceFilter errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  ProcessInstanceFilter hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by flowNodeId */
  ProcessInstanceFilter flowNodeId(final String flowNodeId);

  /** Filter by flowNodeId using {@link StringProperty} */
  ProcessInstanceFilter flowNodeId(final Consumer<StringProperty> fn);

  /** Filter by state */
  ProcessInstanceFilter flowNodeInstanceState(final FlowNodeInstanceState state);

  /** Filter by flowNodeInstanceState using {@link FlowNodeInstanceStateProperty} */
  ProcessInstanceFilter flowNodeInstanceState(final Consumer<FlowNodeInstanceStateProperty> fn);

  /** Filter by hasFlowNodeInstanceIncident */
  ProcessInstanceFilter hasFlowNodeInstanceIncident(final Boolean hasFlowNodeInstanceIncident);

  /** Filter by incidentErrorHashCode */
  ProcessInstanceFilter incidentErrorHashCode(final Integer incidentErrorHashCode);

  /** Filter by or conjunction using {@link ProcessInstanceFilter} consumer */
  ProcessInstanceFilter orFilters(final List<Consumer<ProcessInstanceFilter>> fns);
}
