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

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.MessageSubscriptionStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.MessageSubscriptionStatePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class MessageSubscriptionFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.MessageSubscriptionFilter>
    implements MessageSubscriptionFilter {

  private final io.camunda.client.protocol.rest.MessageSubscriptionFilter filter;

  public MessageSubscriptionFilterImpl() {
    filter = new io.camunda.client.protocol.rest.MessageSubscriptionFilter();
  }

  @Override
  public MessageSubscriptionFilter messageSubscriptionKey(final Long messageSubscriptionKey) {
    return messageSubscriptionKey(f -> f.eq(messageSubscriptionKey));
  }

  @Override
  public MessageSubscriptionFilter messageSubscriptionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setMessageSubscriptionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter processDefinitionId(final String processDefinitionId) {
    return processDefinitionId(f -> f.eq(processDefinitionId));
  }

  @Override
  public MessageSubscriptionFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter processInstanceKey(final Long processInstanceKey) {
    return processInstanceKey(f -> f.eq(processInstanceKey));
  }

  @Override
  public MessageSubscriptionFilter processInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter elementId(final String elementId) {
    return elementId(f -> f.eq(elementId));
  }

  @Override
  public MessageSubscriptionFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter elementInstanceKey(final Long elementInstanceKey) {
    return elementInstanceKey(f -> f.eq(elementInstanceKey));
  }

  @Override
  public MessageSubscriptionFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter messageSubscriptionState(
      final MessageSubscriptionState messageSubscriptionState) {
    return messageSubscriptionState(f -> f.eq(messageSubscriptionState));
  }

  @Override
  public MessageSubscriptionFilter messageSubscriptionState(
      final Consumer<MessageSubscriptionStateProperty> fn) {
    final MessageSubscriptionStateProperty property = new MessageSubscriptionStatePropertyImpl();
    fn.accept(property);
    filter.setMessageSubscriptionState(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter lastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
    return lastUpdatedDate(f -> f.eq(lastUpdatedDate));
  }

  @Override
  public MessageSubscriptionFilter lastUpdatedDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setLastUpdatedDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter messageName(final String messageName) {
    return messageName(f -> f.eq(messageName));
  }

  @Override
  public MessageSubscriptionFilter messageName(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setMessageName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter correlationKey(final String correlationKey) {
    return correlationKey(f -> f.eq(correlationKey));
  }

  @Override
  public MessageSubscriptionFilter correlationKey(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setCorrelationKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public MessageSubscriptionFilter tenantId(final String tenantId) {
    return tenantId(f -> f.eq(tenantId));
  }

  @Override
  public MessageSubscriptionFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.MessageSubscriptionFilter getSearchRequestProperty() {
    return filter;
  }
}
