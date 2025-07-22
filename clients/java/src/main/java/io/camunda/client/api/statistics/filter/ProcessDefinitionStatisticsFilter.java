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
import io.camunda.client.api.search.filter.ProcessInstanceFilterBase;
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessDefinitionStatisticsFilter extends ProcessDefinitionStatisticsFilterBase {

  /** Filter by processInstanceKey */
  @Override
  ProcessDefinitionStatisticsFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  @Override
  ProcessDefinitionStatisticsFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter parentProcessInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentElementInstanceKey */
  @Override
  ProcessDefinitionStatisticsFilter parentElementInstanceKey(final Long parentElementInstanceKey);

  /** Filter by parentElementInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter parentElementInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  @Override
  ProcessDefinitionStatisticsFilter startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  @Override
  ProcessDefinitionStatisticsFilter endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  @Override
  ProcessDefinitionStatisticsFilter state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  @Override
  ProcessDefinitionStatisticsFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  @Override
  ProcessDefinitionStatisticsFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  @Override
  ProcessDefinitionStatisticsFilter variables(
      final List<Consumer<VariableValueFilter>> variableValueFilters);

  /** Filter by variables map */
  @Override
  ProcessDefinitionStatisticsFilter variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  @Override
  ProcessDefinitionStatisticsFilter batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  @Override
  ProcessDefinitionStatisticsFilter batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  @Override
  ProcessDefinitionStatisticsFilter errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  @Override
  ProcessDefinitionStatisticsFilter errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  @Override
  ProcessDefinitionStatisticsFilter hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by elementId */
  @Override
  ProcessDefinitionStatisticsFilter elementId(final String elementId);

  /** Filter by elementId using {@link StringProperty} */
  @Override
  ProcessDefinitionStatisticsFilter elementId(final Consumer<StringProperty> fn);

  /** Filter by elementInstanceState */
  @Override
  ProcessDefinitionStatisticsFilter elementInstanceState(final ElementInstanceState state);

  /** Filter by elementInstanceState using {@link ElementInstanceStateProperty} */
  @Override
  ProcessDefinitionStatisticsFilter elementInstanceState(
      final Consumer<ElementInstanceStateProperty> fn);

  /** Filter by hasElementInstanceIncident */
  @Override
  ProcessDefinitionStatisticsFilter hasElementInstanceIncident(
      final Boolean hasElementInstanceIncident);

  /** Filter by incidentErrorHashCode */
  @Override
  ProcessDefinitionStatisticsFilter incidentErrorHashCode(final Integer incidentErrorHashCode);

  /** Filter by incidentErrorHashCode using {@link IntegerProperty} */
  @Override
  ProcessDefinitionStatisticsFilter incidentErrorHashCode(final Consumer<IntegerProperty> fn);

  /** Filter by or conjunction using {@link ProcessInstanceFilterBase} consumer */
  ProcessDefinitionStatisticsFilter orFilters(
      List<Consumer<ProcessDefinitionStatisticsFilterBase>> filters);
}
