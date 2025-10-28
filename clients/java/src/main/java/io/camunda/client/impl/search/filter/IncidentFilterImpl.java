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
import io.camunda.client.api.search.filter.IncidentFilter;
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

public class IncidentFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.IncidentFilter>
    implements IncidentFilter {

  private final io.camunda.client.protocol.rest.IncidentFilter filter;

  public IncidentFilterImpl() {
    filter = new io.camunda.client.protocol.rest.IncidentFilter();
  }

  @Override
  public IncidentFilter incidentKey(final Long value) {
    incidentKey(b -> b.eq(value));
    return this;
  }

  @Override
  public IncidentFilter incidentKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setIncidentKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionKey(final Long value) {
    processDefinitionKey(b -> b.eq(value));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionId(final String value) {
    processDefinitionId(b -> b.eq(value));
    return this;
  }

  @Override
  public IncidentFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.processDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter processInstanceKey(final Long value) {
    processInstanceKey(b -> b.eq(value));
    return this;
  }

  @Override
  public IncidentFilter processInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter errorType(final IncidentErrorType errorType) {
    return errorType(b -> b.eq(errorType));
  }

  @Override
  public IncidentFilter errorType(final Consumer<IncidentErrorTypeProperty> fn) {
    final IncidentErrorTypeProperty property = new IncidentErrorTypePropertyImpl();
    fn.accept(property);
    filter.setErrorType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter errorMessage(final String errorMessage) {
    return errorMessage(b -> b.eq(errorMessage));
  }

  @Override
  public IncidentFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter elementId(final String value) {
    return elementId(b -> b.eq(value));
  }

  @Override
  public IncidentFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter elementInstanceKey(final Long value) {
    return elementInstanceKey(b -> b.eq(value));
  }

  @Override
  public IncidentFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter creationTime(final OffsetDateTime creationTime) {
    return creationTime(b -> b.eq(creationTime));
  }

  @Override
  public IncidentFilter creationTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter state(final IncidentState value) {
    return state(b -> b.eq(value));
  }

  @Override
  public IncidentFilter state(final Consumer<IncidentStateProperty> fn) {
    final IncidentStateProperty property = new IncidentStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter jobKey(final Long value) {
    return jobKey(b -> b.eq(value));
  }

  @Override
  public IncidentFilter jobKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setJobKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public IncidentFilter tenantId(final String value) {
    return tenantId(b -> b.eq(value));
  }

  @Override
  public IncidentFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.IncidentFilter getSearchRequestProperty() {
    return filter;
  }
}
