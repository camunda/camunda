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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessInstanceFilterBase extends SearchRequestFilter {

  /** Filter by processInstanceKey */
  ProcessInstanceFilterBase processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilterBase processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by processDefinitionId */
  ProcessInstanceFilterBase processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionId using {@link StringProperty} */
  ProcessInstanceFilterBase processDefinitionId(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionName */
  ProcessInstanceFilterBase processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionName using {@link StringProperty} consumer */
  ProcessInstanceFilterBase processDefinitionName(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionVersion */
  ProcessInstanceFilterBase processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersion using {@link IntegerProperty} consumer */
  ProcessInstanceFilterBase processDefinitionVersion(final Consumer<IntegerProperty> fn);

  /** Filter by processDefinitionVersionTag */
  ProcessInstanceFilterBase processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionVersionTag using {@link StringProperty} consumer */
  ProcessInstanceFilterBase processDefinitionVersionTag(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionKey */
  ProcessInstanceFilterBase processDefinitionKey(final Long processDefinitionKey);

  /** Filter by processDefinitionKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilterBase processDefinitionKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  ProcessInstanceFilterBase parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilterBase parentProcessInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentElementInstanceKey */
  ProcessInstanceFilterBase parentElementInstanceKey(final Long parentElementInstanceKey);

  /** Filter by parentElementInstanceKey using {@link BasicLongProperty} consumer */
  ProcessInstanceFilterBase parentElementInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  ProcessInstanceFilterBase startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  ProcessInstanceFilterBase startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  ProcessInstanceFilterBase endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  ProcessInstanceFilterBase endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  ProcessInstanceFilterBase state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  ProcessInstanceFilterBase state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  ProcessInstanceFilterBase hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  ProcessInstanceFilterBase tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  ProcessInstanceFilterBase tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  ProcessInstanceFilterBase variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters);

  /** Filter by variables map */
  ProcessInstanceFilterBase variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  ProcessInstanceFilterBase batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  ProcessInstanceFilterBase batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  ProcessInstanceFilterBase errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  ProcessInstanceFilterBase errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  ProcessInstanceFilterBase hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by elementId */
  ProcessInstanceFilterBase elementId(final String elementId);

  /** Filter by elementId using {@link StringProperty} */
  ProcessInstanceFilterBase elementId(final Consumer<StringProperty> fn);

  /** Filter by state */
  ProcessInstanceFilterBase elementInstanceState(final ElementInstanceState state);

  /** Filter by elementInstanceState using {@link ElementInstanceStateProperty} */
  ProcessInstanceFilterBase elementInstanceState(final Consumer<ElementInstanceStateProperty> fn);

  /** Filter by hasElementInstanceIncident */
  ProcessInstanceFilterBase hasElementInstanceIncident(final Boolean hasElementInstanceIncident);

  /** Filter by incidentErrorHashCode */
  ProcessInstanceFilterBase incidentErrorHashCode(final Integer incidentErrorHashCode);
}
