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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.ElementInstanceStatePropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class ElementInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ElementInstanceFilter>
    implements ElementInstanceFilter {

  private final io.camunda.client.protocol.rest.ElementInstanceFilter filter;

  public ElementInstanceFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ElementInstanceFilter();
  }

  @Override
  public ElementInstanceFilter elementInstanceKey(final long value) {
    filter.elementInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public ElementInstanceFilter processDefinitionKey(final long value) {
    filter.setProcessDefinitionKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public ElementInstanceFilter processDefinitionId(final String value) {
    filter.processDefinitionId(value);
    return this;
  }

  @Override
  public ElementInstanceFilter processInstanceKey(final long value) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public ElementInstanceFilter elementId(final String value) {
    filter.setElementId(value);
    return this;
  }

  @Override
  public ElementInstanceFilter elementName(final String value) {
    filter.setElementName(value);
    return this;
  }

  @Override
  public ElementInstanceFilter state(final ElementInstanceState value) {
    return state(b -> b.eq(value));
  }

  @Override
  public ElementInstanceFilter state(final Consumer<ElementInstanceStateProperty> fn) {
    final ElementInstanceStateProperty property = new ElementInstanceStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceFilter type(final ElementInstanceType value) {
    filter.setType(
        EnumUtil.convert(
            value, io.camunda.client.protocol.rest.ElementInstanceFilter.TypeEnum.class));
    return this;
  }

  @Override
  public ElementInstanceFilter hasIncident(final boolean value) {
    filter.hasIncident(value);
    return this;
  }

  @Override
  public ElementInstanceFilter incidentKey(final long value) {
    filter.setIncidentKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  public ElementInstanceFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  public ElementInstanceFilter startDate(final OffsetDateTime startDate) {
    startDate(b -> b.eq(startDate));
    return this;
  }

  @Override
  public ElementInstanceFilter startDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setStartDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceFilter endDate(final OffsetDateTime endDate) {
    endDate(b -> b.eq(endDate));
    return this;
  }

  @Override
  public ElementInstanceFilter endDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setEndDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceFilter elementInstanceScopeKey(final long value) {
    filter.setElementInstanceScopeKey(ParseUtil.keyToString(value));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ElementInstanceFilter getSearchRequestProperty() {
    return filter;
  }
}
