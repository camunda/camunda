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

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.filter.AgentInstanceFilter;
import io.camunda.client.api.search.filter.builder.AgentInstanceStatusProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.AgentInstanceStatusPropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.AgentInstanceKeyFilterProperty;
import io.camunda.client.protocol.rest.BasicStringFilterProperty;
import java.util.function.Consumer;

public class AgentInstanceFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.AgentInstanceFilter>
    implements AgentInstanceFilter {

  private final io.camunda.client.protocol.rest.AgentInstanceFilter filter;

  public AgentInstanceFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AgentInstanceFilter();
  }

  @Override
  protected io.camunda.client.protocol.rest.AgentInstanceFilter getSearchRequestProperty() {
    return filter;
  }

  @Override
  public AgentInstanceFilter agentInstanceKey(final long value) {
    return agentInstanceKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter agentInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongPropertyImpl property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setAgentInstanceKey(
        toAgentInstanceKeyFilterProperty(provideSearchRequestProperty(property)));
    return this;
  }

  @Override
  public AgentInstanceFilter status(final AgentInstanceStatus value) {
    return status(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter status(final Consumer<AgentInstanceStatusProperty> fn) {
    final AgentInstanceStatusProperty property = new AgentInstanceStatusPropertyImpl();
    fn.accept(property);
    filter.setStatus(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter elementId(final String value) {
    return elementId(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter processInstanceKey(final long value) {
    return processInstanceKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter processInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter processDefinitionKey(final long value) {
    return processDefinitionKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter processDefinitionId(final String value) {
    return processDefinitionId(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter processDefinitionVersion(final int value) {
    return processDefinitionVersion(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter processDefinitionVersion(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersion(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter processDefinitionVersionTag(final String value) {
    return processDefinitionVersionTag(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter processDefinitionVersionTag(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersionTag(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter tenantId(final String value) {
    return tenantId(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter creationDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCreationDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter lastUpdatedDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setLastUpdatedDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter completionDate(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setCompletionDate(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceFilter elementInstanceKey(final long value) {
    return elementInstanceKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.addElementInstanceKeysItem(provideSearchRequestProperty(property));
    return this;
  }

  private AgentInstanceKeyFilterProperty toAgentInstanceKeyFilterProperty(
      final BasicStringFilterProperty src) {
    final AgentInstanceKeyFilterProperty dest = new AgentInstanceKeyFilterProperty();
    dest.set$Eq(src.get$Eq());
    dest.set$Neq(src.get$Neq());
    dest.set$Exists(src.get$Exists());
    dest.set$In(src.get$In());
    dest.set$NotIn(src.get$NotIn());
    return dest;
  }
}
