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

import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.filter.AgentDefinitionFilter;
import io.camunda.client.api.search.filter.builder.AgentDefinitionTypeProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.AgentDefinitionTypePropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class AgentDefinitionFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.AgentDefinitionFilter>
    implements AgentDefinitionFilter {

  private final io.camunda.client.protocol.rest.AgentDefinitionFilter filter;

  public AgentDefinitionFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AgentDefinitionFilter();
  }

  @Override
  protected io.camunda.client.protocol.rest.AgentDefinitionFilter getSearchRequestProperty() {
    return filter;
  }

  @Override
  public AgentDefinitionFilter agentDefinitionKey(final long value) {
    return agentDefinitionKey(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter agentDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setAgentDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter agentType(final AgentDefinitionType value) {
    return agentType(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter agentType(final Consumer<AgentDefinitionTypeProperty> fn) {
    final AgentDefinitionTypeProperty property = new AgentDefinitionTypePropertyImpl();
    fn.accept(property);
    filter.setAgentType(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter name(final String value) {
    return name(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter elementId(final String value) {
    return elementId(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter elementId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setElementId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter processDefinitionId(final String value) {
    return processDefinitionId(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter processDefinitionId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter processDefinitionKey(final long value) {
    return processDefinitionKey(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter processDefinitionKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter processDefinitionVersion(final int value) {
    return processDefinitionVersion(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter processDefinitionVersion(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersion(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter processDefinitionVersionTag(final String value) {
    return processDefinitionVersionTag(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter processDefinitionVersionTag(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setProcessDefinitionVersionTag(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentDefinitionFilter tenantId(final String value) {
    return tenantId(f -> f.eq(value));
  }

  @Override
  public AgentDefinitionFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }
}
