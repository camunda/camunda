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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessInstanceFilter extends ProcessInstanceFilterBase {

  /** Filter by processInstanceKey */
  @Override
  ProcessInstanceFilter processInstanceKey(final Long processInstanceKey);

  /** Filter by processInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessInstanceFilter processInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by processDefinitionId */
  @Override
  ProcessInstanceFilter processDefinitionId(final String processDefinitionId);

  /** Filter by processDefinitionId using {@link StringProperty} */
  @Override
  ProcessInstanceFilter processDefinitionId(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionName */
  @Override
  ProcessInstanceFilter processDefinitionName(final String processDefinitionName);

  /** Filter by processDefinitionName using {@link StringProperty} consumer */
  @Override
  ProcessInstanceFilter processDefinitionName(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionVersion */
  @Override
  ProcessInstanceFilter processDefinitionVersion(final Integer processDefinitionVersion);

  /** Filter by processDefinitionVersion using {@link IntegerProperty} consumer */
  @Override
  ProcessInstanceFilter processDefinitionVersion(final Consumer<IntegerProperty> fn);

  /** Filter by processDefinitionVersionTag */
  @Override
  ProcessInstanceFilter processDefinitionVersionTag(final String processDefinitionVersionTag);

  /** Filter by processDefinitionVersionTag using {@link StringProperty} consumer */
  @Override
  ProcessInstanceFilter processDefinitionVersionTag(final Consumer<StringProperty> fn);

  /** Filter by processDefinitionKey */
  @Override
  ProcessInstanceFilter processDefinitionKey(final Long processDefinitionKey);

  /** Filter by processDefinitionKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessInstanceFilter processDefinitionKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentProcessInstanceKey */
  @Override
  ProcessInstanceFilter parentProcessInstanceKey(final Long parentProcessInstanceKey);

  /** Filter by parentProcessInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessInstanceFilter parentProcessInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by parentElementInstanceKey */
  @Override
  ProcessInstanceFilter parentElementInstanceKey(final Long parentElementInstanceKey);

  /** Filter by parentElementInstanceKey using {@link BasicLongProperty} consumer */
  @Override
  ProcessInstanceFilter parentElementInstanceKey(final Consumer<BasicLongProperty> fn);

  /** Filter by startDate */
  @Override
  ProcessInstanceFilter startDate(final OffsetDateTime startDate);

  /** Filter by startDate using {@link DateTimeProperty} consumer */
  @Override
  ProcessInstanceFilter startDate(final Consumer<DateTimeProperty> fn);

  /** Filter by endDate */
  @Override
  ProcessInstanceFilter endDate(final OffsetDateTime endDate);

  /** Filter by endDate using {@link DateTimeProperty} consumer */
  @Override
  ProcessInstanceFilter endDate(final Consumer<DateTimeProperty> fn);

  /** Filter by state */
  @Override
  ProcessInstanceFilter state(final ProcessInstanceState state);

  /** Filter by state using {@link ProcessInstanceStateProperty} consumer */
  @Override
  ProcessInstanceFilter state(final Consumer<ProcessInstanceStateProperty> fn);

  /** Filter by hasIncident */
  @Override
  ProcessInstanceFilter hasIncident(final Boolean hasIncident);

  /** Filter by tenantId */
  @Override
  ProcessInstanceFilter tenantId(final String tenantId);

  /** Filter by tenantId using {@link StringProperty} consumer */
  @Override
  ProcessInstanceFilter tenantId(final Consumer<StringProperty> fn);

  /** Filter by variables */
  @Override
  ProcessInstanceFilter variables(
      final List<ProcessInstanceVariableFilterRequest> variableValueFilters);

  /** Filter by variables map */
  @Override
  ProcessInstanceFilter variables(final Map<String, Object> variableValueFilters);

  /** Filter by batchOperationId */
  @Override
  ProcessInstanceFilter batchOperationId(final String batchOperationId);

  /** Filter by batchOperationId using {@link StringProperty} */
  @Override
  ProcessInstanceFilter batchOperationId(final Consumer<StringProperty> fn);

  /** Filter by error message */
  @Override
  ProcessInstanceFilter errorMessage(final String errorMessage);

  /** Filter by error message using {@link StringProperty} consumer */
  @Override
  ProcessInstanceFilter errorMessage(final Consumer<StringProperty> fn);

  /** Filter by hasRetriesLeft */
  @Override
  ProcessInstanceFilter hasRetriesLeft(final Boolean hasRetriesLeft);

  /** Filter by elementId */
  @Override
  ProcessInstanceFilter elementId(final String elementId);

  /** Filter by elementId using {@link StringProperty} */
  @Override
  ProcessInstanceFilter elementId(final Consumer<StringProperty> fn);

  /** Filter by state */
  @Override
  ProcessInstanceFilter elementInstanceState(final ElementInstanceState state);

  /** Filter by elementInstanceState using {@link ElementInstanceStateProperty} */
  @Override
  ProcessInstanceFilter elementInstanceState(final Consumer<ElementInstanceStateProperty> fn);

  /** Filter by hasElementInstanceIncident */
  @Override
  ProcessInstanceFilter hasElementInstanceIncident(final Boolean hasElementInstanceIncident);

  /** Filter by incidentErrorHashCode */
  @Override
  ProcessInstanceFilter incidentErrorHashCode(final Integer incidentErrorHashCode);

  /** Filter by or conjunction using {@link ProcessInstanceFilterBase} consumer */
  ProcessInstanceFilterBase orFilters(List<Consumer<ProcessInstanceFilterBase>> filters);
}
