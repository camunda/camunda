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
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface ElementInstanceFilterBase extends SearchRequestFilter {

  /** Filter element instances by the specified key. */
  ElementInstanceFilterBase elementInstanceKey(final long value);

  /** Filter element instances by process definition key. */
  ElementInstanceFilterBase processDefinitionKey(final long value);

  /** Filter element instances by bpmn process id. */
  ElementInstanceFilterBase processDefinitionId(final String value);

  /** Filter element instances by process instance key. */
  ElementInstanceFilterBase processInstanceKey(final long value);

  /** Filter element instances by element id (exact match). */
  ElementInstanceFilterBase elementId(final String value);

  /** Filter element instances by element id using {@link StringProperty} consumer. */
  ElementInstanceFilterBase elementId(final Consumer<StringProperty> fn);

  /**
   * Filter element instances by element name (exact match). Only works for data created with 8.8 or
   * later.
   */
  ElementInstanceFilterBase elementName(final String value);

  /**
   * Filter element instances by element name using {@link StringProperty} consumer. Only works for
   * data created with 8.8 or later.
   */
  ElementInstanceFilterBase elementName(final Consumer<StringProperty> fn);

  /** Filter element instances by state. */
  ElementInstanceFilterBase state(final ElementInstanceState value);

  /** Filter by state using {@link ElementInstanceStateProperty} consumer. */
  ElementInstanceFilterBase state(final Consumer<ElementInstanceStateProperty> fn);

  /** Filter element instances by type. */
  ElementInstanceFilterBase type(final ElementInstanceType value);

  /** Filter element instances by incident (has an incident). */
  ElementInstanceFilterBase hasIncident(final boolean value);

  /** Filter element instances by incident key. */
  ElementInstanceFilterBase incidentKey(final long value);

  /** Filter element instances by tenant id. */
  ElementInstanceFilterBase tenantId(final String value);

  /** Filter element instances by start date. */
  ElementInstanceFilterBase startDate(final OffsetDateTime value);

  /** Filter element instances by start date using {@link DateTimeProperty} consumer. */
  ElementInstanceFilterBase startDate(final Consumer<DateTimeProperty> startDate);

  /** Filter element instances by end date. */
  ElementInstanceFilterBase endDate(final OffsetDateTime value);

  /** Filter element instances by end date using {@link DateTimeProperty} consumer. */
  ElementInstanceFilterBase endDate(final Consumer<DateTimeProperty> endDate);

  /** Filter element instances by the specified scope key. */
  ElementInstanceFilterBase elementInstanceScopeKey(final long value);
}
