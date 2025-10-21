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

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.ElementInstanceIncidentFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IncidentErrorTypeProperty;
import io.camunda.client.api.search.filter.builder.IncidentStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IncidentErrorTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IncidentStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class ElementInstanceIncidentFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ElementInstanceIncidentFilter>
    implements ElementInstanceIncidentFilter {

  private final io.camunda.client.protocol.rest.ElementInstanceIncidentFilter filter;

  public ElementInstanceIncidentFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ElementInstanceIncidentFilter();
  }

  @Override
  public ElementInstanceIncidentFilter incidentKey(final Long value) {
    incidentKey(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter incidentKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setIncidentKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter processDefinitionKey(final Long value) {
    processDefinitionKey(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter processDefinitionId(final String value) {
    processDefinitionId(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter errorType(final IncidentErrorType errorType) {
    errorType(b -> b.eq(errorType));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter errorType(final Consumer<IncidentErrorTypeProperty> fn) {
    final IncidentErrorTypeProperty property = new IncidentErrorTypePropertyImpl();
    fn.accept(property);
    filter.setErrorType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter errorMessage(final String errorMessage) {
    errorMessage(b -> b.eq(errorMessage));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter elementId(final String value) {
    elementId(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter elementInstanceKey(final Long value) {
    elementInstanceKey(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter creationTime(final OffsetDateTime creationTime) {
    creationTime(b -> b.eq(creationTime));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter creationTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter state(final IncidentState value) {
    state(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter state(final Consumer<IncidentStateProperty> fn) {
    final IncidentStateProperty property = new IncidentStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter jobKey(final Long value) {
    jobKey(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter jobKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setJobKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter tenantId(final String value) {
    tenantId(b -> b.eq(value));
    return this;
  }

  @Override
  public ElementInstanceIncidentFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ElementInstanceIncidentFilter
      getSearchRequestProperty() {
    return filter;
  }
}
