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
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface ElementInstanceFilter extends SearchRequestFilter {

  /**
   * Filters element instances by the specified key.
   *
   * @param value the key of element instance
   * @return the updated filter
   */
  ElementInstanceFilter elementInstanceKey(final long value);

  /**
   * Filters element instances by process definition key.
   *
   * @param value the process definition key of element instance
   * @return the updated filter
   */
  ElementInstanceFilter processDefinitionKey(final long value);

  /**
   * Filters element instances by bpmn process id.
   *
   * @param value the bpmn process id of element instance
   * @return the updated filter
   */
  ElementInstanceFilter processDefinitionId(final String value);

  /**
   * Filters element instances by process instance key.
   *
   * @param value the process instance key of element instance
   * @return the updated filter
   */
  ElementInstanceFilter processInstanceKey(final long value);

  /**
   * Filters element instances by element id.
   *
   * @param value the element id of element instance
   * @return the updated filter
   */
  ElementInstanceFilter elementId(final String value);

  /**
   * Filters element instances by element name. This only works for data created with 8.8 and
   * onwards. Instances from prior versions don't contain this data.
   *
   * @param value the element name of element instance
   * @return the updated filter
   */
  ElementInstanceFilter elementName(final String value);

  /**
   * Filters element instances by state.
   *
   * @param value the state of element instance
   * @return the updated filter
   */
  ElementInstanceFilter state(final ElementInstanceState value);

  /** Filter by state using {@link ElementInstanceStateProperty} consumer */
  ElementInstanceFilter state(final Consumer<ElementInstanceStateProperty> fn);

  /**
   * Filters element instances by type.
   *
   * @param value the type of element instance
   * @return the updated filter
   */
  ElementInstanceFilter type(final ElementInstanceType value);

  /**
   * Filters element instances by incident (has an incident)
   *
   * @param value has the element instance an incident
   * @return the updated filter
   */
  ElementInstanceFilter hasIncident(final boolean value);

  /**
   * Filters element instances by incident key.
   *
   * @param value the incident key for element instance
   * @return the updated filter
   */
  ElementInstanceFilter incidentKey(final long value);

  /**
   * Filters element instances by tenant id.
   *
   * @param value the tenant id for element instance
   * @return the updated filter
   */
  ElementInstanceFilter tenantId(final String value);

  /**
   * Filters element instances by start date.
   *
   * @param value the start date of the element instance
   * @return the updated filter
   */
  ElementInstanceFilter startDate(final OffsetDateTime value);

  /**
   * Filters element instances by the specified {@link DateTimeProperty} start date.
   *
   * @param startDate the start date of the element instance
   * @return the updated filter
   */
  ElementInstanceFilter startDate(final Consumer<DateTimeProperty> startDate);

  /**
   * Filters element instances by end date.
   *
   * @param value the end date of the element instance
   * @return the updated filter
   */
  ElementInstanceFilter endDate(final OffsetDateTime value);

  /**
   * Filters element instances by the specified {@link DateTimeProperty} end date.
   *
   * @param endDate the end date of the element instance
   * @return the updated filter
   */
  ElementInstanceFilter endDate(final Consumer<DateTimeProperty> endDate);

  /**
   * Filters element instances by the specified scope key.
   *
   * @param value the scope key of the element instance
   * @return the updated filter
   */
  ElementInstanceFilter elementInstanceScopeKey(final long value);
}
