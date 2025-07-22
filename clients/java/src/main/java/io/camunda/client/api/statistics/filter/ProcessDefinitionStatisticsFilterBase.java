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
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.statistics.request.StatisticsRequest.StatisticsRequestFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessDefinitionStatisticsFilterBase extends StatisticsRequestFilter {

  /** Filter by processInstanceKey */
  ProcessDefinitionStatisticsFilterBase processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilterBase processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  ProcessDefinitionStatisticsFilterBase parentProcessInstanceKey(
      final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilterBase parentProcessInstanceKey(
      final Consumer<BasicLongProperty> fn);

  /** Filter by parentElementInstanceKey */
  ProcessDefinitionStatisticsFilterBase parentElementInstanceKey(
      final Long parentElementInstanceKey);

  /** Filter by parentElementInstanceKey using {@link BasicLongProperty} consumer */
  ProcessDefinitionStatisticsFilterBase parentElementInstanceKey(
      final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  ProcessDefinitionStatisticsFilterBase startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  ProcessDefinitionStatisticsFilterBase startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  ProcessDefinitionStatisticsFilterBase endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  ProcessDefinitionStatisticsFilterBase endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  ProcessDefinitionStatisticsFilterBase state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  ProcessDefinitionStatisticsFilterBase state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  ProcessDefinitionStatisticsFilterBase hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessDefinitionStatisticsFilterBase tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  ProcessDefinitionStatisticsFilterBase tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  ProcessDefinitionStatisticsFilterBase variables(
      final List<Consumer<VariableValueFilter>> variableValueFilters);

  /** Filter by variables map */
  ProcessDefinitionStatisticsFilterBase variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  ProcessDefinitionStatisticsFilterBase batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  ProcessDefinitionStatisticsFilterBase batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  ProcessDefinitionStatisticsFilterBase errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  ProcessDefinitionStatisticsFilterBase errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  ProcessDefinitionStatisticsFilterBase hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by elementId */
  ProcessDefinitionStatisticsFilterBase elementId(final String elementId);

  /** Filter by elementId using {@link StringProperty} */
  ProcessDefinitionStatisticsFilterBase elementId(final Consumer<StringProperty> fn);

  /** Filter by elementInstanceState */
  ProcessDefinitionStatisticsFilterBase elementInstanceState(final ElementInstanceState state);

  /** Filter by elementInstanceState using {@link ElementInstanceStateProperty} */
  ProcessDefinitionStatisticsFilterBase elementInstanceState(
      final Consumer<ElementInstanceStateProperty> fn);

  /** Filter by hasElementInstanceIncident */
  ProcessDefinitionStatisticsFilterBase hasElementInstanceIncident(
      final Boolean hasElementInstanceIncident);

  /** Filter by incidentErrorHashCode */
  ProcessDefinitionStatisticsFilterBase incidentErrorHashCode(final Integer incidentErrorHashCode);

  /** Filter by incidentErrorHashCode using {@link IntegerProperty} */
  ProcessDefinitionStatisticsFilterBase incidentErrorHashCode(final Consumer<IntegerProperty> fn);
}
