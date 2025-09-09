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

import io.camunda.client.api.search.filter.CorrelatedMessageFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class CorrelatedMessageFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.CorrelatedMessageFilter>
    implements CorrelatedMessageFilter {

  private final io.camunda.client.protocol.rest.CorrelatedMessageFilter filter;

  public CorrelatedMessageFilterImpl() {
    filter = new io.camunda.client.protocol.rest.CorrelatedMessageFilter();
  }

  @Override
  public CorrelatedMessageFilter correlationKey(final String correlationKey) {
    return correlationKey(f -> f.eq(correlationKey));
  }

  @Override
  public CorrelatedMessageFilter correlationKey(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setCorrelationKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter correlationTime(final OffsetDateTime correlationTime) {
    return correlationTime(f -> f.eq(correlationTime));
  }

  @Override
  public CorrelatedMessageFilter correlationTime(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCorrelationTime(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter elementId(final String elementId) {
    return elementId(f -> f.eq(elementId));
  }

  @Override
  public CorrelatedMessageFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter elementInstanceKey(final Long elementInstanceKey) {
    return elementInstanceKey(f -> f.eq(elementInstanceKey));
  }

  @Override
  public CorrelatedMessageFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter messageKey(final Long messageKey) {
    return messageKey(f -> f.eq(messageKey));
  }

  @Override
  public CorrelatedMessageFilter messageKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setMessageKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter messageName(final String messageName) {
    return messageName(f -> f.eq(messageName));
  }

  @Override
  public CorrelatedMessageFilter messageName(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setMessageName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter partitionId(final Integer partitionId) {
    return partitionId(f -> f.eq(partitionId));
  }

  @Override
  public CorrelatedMessageFilter partitionId(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setPartitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter processDefinitionId(final String processDefinitionId) {
    return processDefinitionId(f -> f.eq(processDefinitionId));
  }

  @Override
  public CorrelatedMessageFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter processDefinitionKey(final Long processDefinitionKey) {
    return processDefinitionKey(f -> f.eq(processDefinitionKey));
  }

  @Override
  public CorrelatedMessageFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter processInstanceKey(final Long processInstanceKey) {
    return processInstanceKey(f -> f.eq(processInstanceKey));
  }

  @Override
  public CorrelatedMessageFilter processInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter subscriptionKey(final Long subscriptionKey) {
    return subscriptionKey(f -> f.eq(subscriptionKey));
  }

  @Override
  public CorrelatedMessageFilter subscriptionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setSubscriptionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public CorrelatedMessageFilter tenantId(final String tenantId) {
    return tenantId(f -> f.eq(tenantId));
  }

  @Override
  public CorrelatedMessageFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.CorrelatedMessageFilter getSearchRequestProperty() {
    return filter;
  }
}