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
import io.camunda.client.api.search.filter.ProcessInstanceIncidentFilter;
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

public class ProcessInstanceIncidentFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.ProcessInstanceIncidentFilter>
    implements ProcessInstanceIncidentFilter {

  private final io.camunda.client.protocol.rest.ProcessInstanceIncidentFilter filter;

  public ProcessInstanceIncidentFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ProcessInstanceIncidentFilter();
  }

  @Override
  public ProcessInstanceIncidentFilter incidentKey(final Long value) {
    return incidentKey(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter incidentKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setIncidentKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter processDefinitionKey(final Long value) {
    return processDefinitionKey(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter processDefinitionId(final String value) {
    return processDefinitionId(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter errorType(final IncidentErrorType errorType) {
    return errorType(b -> b.eq(errorType));
  }

  @Override
  public ProcessInstanceIncidentFilter errorType(final Consumer<IncidentErrorTypeProperty> fn) {
    final IncidentErrorTypeProperty property = new IncidentErrorTypePropertyImpl();
    fn.accept(property);
    filter.setErrorType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter errorMessage(final String errorMessage) {
    return errorMessage(b -> b.eq(errorMessage));
  }

  @Override
  public ProcessInstanceIncidentFilter errorMessage(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setErrorMessage(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter elementId(final String value) {
    return elementId(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter elementInstanceKey(final Long value) {
    return elementInstanceKey(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter creationTime(final OffsetDateTime creationTime) {
    return creationTime(b -> b.eq(creationTime));
  }

  @Override
  public ProcessInstanceIncidentFilter creationTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter state(final IncidentState value) {
    return state(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter state(final Consumer<IncidentStateProperty> fn) {
    final IncidentStateProperty property = new IncidentStatePropertyImpl();
    fn.accept(property);
    filter.setState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter jobKey(final Long value) {
    return jobKey(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter jobKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setJobKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ProcessInstanceIncidentFilter tenantId(final String value) {
    return tenantId(b -> b.eq(value));
  }

  @Override
  public ProcessInstanceIncidentFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ProcessInstanceIncidentFilter
      getSearchRequestProperty() {
    return filter;
  }
}
