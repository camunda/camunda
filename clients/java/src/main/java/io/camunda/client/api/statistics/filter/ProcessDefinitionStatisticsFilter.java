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
package io.camunda.client.api.statistics.filter;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceVariableFilterRequest;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.request.StatisticsRequest.StatisticsRequestFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessDefinitionStatisticsFilter extends StatisticsRequestFilter {

  /** Filter by processInstanceKey */
  ProcessDefinitionStatisticsFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  ProcessDefinitionStatisticsFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilter parentProcessInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentElementInstanceKey */
  ProcessDefinitionStatisticsFilter parentElementInstanceKey(final Long parentElementInstanceKey);

  /** Filter by parentElementInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilter parentElementInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  ProcessDefinitionStatisticsFilter startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  ProcessDefinitionStatisticsFilter startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  ProcessDefinitionStatisticsFilter endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  ProcessDefinitionStatisticsFilter endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  ProcessDefinitionStatisticsFilter state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  ProcessDefinitionStatisticsFilter state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  ProcessDefinitionStatisticsFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessDefinitionStatisticsFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  ProcessDefinitionStatisticsFilter tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  ProcessDefinitionStatisticsFilter variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters);

  /** Filter by variables map */
  ProcessDefinitionStatisticsFilter variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  ProcessDefinitionStatisticsFilter batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  ProcessDefinitionStatisticsFilter batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  ProcessDefinitionStatisticsFilter errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  ProcessDefinitionStatisticsFilter errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  ProcessDefinitionStatisticsFilter hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by elementId */
  ProcessDefinitionStatisticsFilter elementId(final String elementId);

  /** Filter by elementId using {@link StringProperty} */
  ProcessDefinitionStatisticsFilter elementId(final Consumer<StringProperty> fn);

  /** Filter by elementInstanceState */
  ProcessDefinitionStatisticsFilter elementInstanceState(final ElementInstanceState state);

  /** Filter by elementInstanceState using {@link ElementInstanceStateProperty} */
  ProcessDefinitionStatisticsFilter elementInstanceState(
      final Consumer<ElementInstanceStateProperty> fn);

  /** Filter by hasElementInstanceIncident */
  ProcessDefinitionStatisticsFilter hasElementInstanceIncident(
      final Boolean hasElementInstanceIncident);

  /** Filter by incidentErrorHashCode */
  ProcessDefinitionStatisticsFilter incidentErrorHashCode(final Integer incidentErrorHashCode);
}
