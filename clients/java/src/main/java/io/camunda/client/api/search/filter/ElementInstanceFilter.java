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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

public interface ElementInstanceFilter extends ElementInstanceFilterBase {

  @Override
  ElementInstanceFilter elementInstanceKey(final long value);

  @Override
  ElementInstanceFilter processDefinitionKey(final long value);

  @Override
  ElementInstanceFilter processDefinitionId(final String value);

  @Override
  ElementInstanceFilter processInstanceKey(final long value);

  @Override
  ElementInstanceFilter elementId(final String value);

  @Override
  ElementInstanceFilter elementId(final Consumer<StringProperty> fn);

  @Override
  ElementInstanceFilter elementName(final String value);

  @Override
  ElementInstanceFilter elementName(final Consumer<StringProperty> fn);

  @Override
  ElementInstanceFilter state(final ElementInstanceState value);

  @Override
  ElementInstanceFilter state(final Consumer<ElementInstanceStateProperty> fn);

  @Override
  ElementInstanceFilter type(final ElementInstanceType value);

  @Override
  ElementInstanceFilter hasIncident(final boolean value);

  @Override
  ElementInstanceFilter incidentKey(final long value);

  @Override
  ElementInstanceFilter tenantId(final String value);

  @Override
  ElementInstanceFilter startDate(final OffsetDateTime value);

  @Override
  ElementInstanceFilter startDate(final Consumer<DateTimeProperty> startDate);

  @Override
  ElementInstanceFilter endDate(final OffsetDateTime value);

  @Override
  ElementInstanceFilter endDate(final Consumer<DateTimeProperty> endDate);

  @Override
  ElementInstanceFilter elementInstanceScopeKey(final long value);

  /** Filter by or conjunction using {@link ElementInstanceFilterBase} consumer. */
  ElementInstanceFilterBase orFilters(List<Consumer<ElementInstanceFilterBase>> filters);
}
