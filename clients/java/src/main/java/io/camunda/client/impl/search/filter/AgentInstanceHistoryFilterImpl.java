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

import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.filter.AgentInstanceHistoryFilter;
import io.camunda.client.api.search.filter.builder.AgentInstanceHistoryCommitStatusProperty;
import io.camunda.client.api.search.filter.builder.AgentInstanceHistoryRoleProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.impl.search.filter.builder.AgentInstanceHistoryCommitStatusPropertyImpl;
import io.camunda.client.impl.search.filter.builder.AgentInstanceHistoryRolePropertyImpl;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.DateTimePropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.AgentHistoryItemKeyFilterProperty;
import io.camunda.client.protocol.rest.BasicStringFilterProperty;
import java.util.function.Consumer;

public class AgentInstanceHistoryFilterImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.AgentInstanceHistoryFilter>
    implements AgentInstanceHistoryFilter {

  private final io.camunda.client.protocol.rest.AgentInstanceHistoryFilter filter;

  public AgentInstanceHistoryFilterImpl() {
    filter = new io.camunda.client.protocol.rest.AgentInstanceHistoryFilter();
  }

  @Override
  protected io.camunda.client.protocol.rest.AgentInstanceHistoryFilter getSearchRequestProperty() {
    return filter;
  }

  @Override
  public AgentInstanceHistoryFilter historyItemKey(final long value) {
    return historyItemKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter historyItemKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongPropertyImpl property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setHistoryItemKey(
        toAgentHistoryItemKeyFilterProperty(provideSearchRequestProperty(property)));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter role(final AgentInstanceHistoryRole value) {
    return role(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter role(final Consumer<AgentInstanceHistoryRoleProperty> fn) {
    final AgentInstanceHistoryRoleProperty property = new AgentInstanceHistoryRolePropertyImpl();
    fn.accept(property);
    filter.setRole(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter elementInstanceKey(final long value) {
    return elementInstanceKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter elementInstanceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongPropertyImpl property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setElementInstanceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter jobKey(final long value) {
    return jobKey(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter jobKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongPropertyImpl property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setJobKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter iteration(final int value) {
    return iteration(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter iteration(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setIteration(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter commitStatus(final AgentInstanceHistoryCommitStatus value) {
    return commitStatus(f -> f.eq(value));
  }

  @Override
  public AgentInstanceHistoryFilter commitStatus(
      final Consumer<AgentInstanceHistoryCommitStatusProperty> fn) {
    final AgentInstanceHistoryCommitStatusProperty property =
        new AgentInstanceHistoryCommitStatusPropertyImpl();
    fn.accept(property);
    filter.setCommitStatus(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public AgentInstanceHistoryFilter producedAt(final Consumer<DateTimeProperty> fn) {
    final DateTimeProperty property = new DateTimePropertyImpl();
    fn.accept(property);
    filter.setProducedAt(provideSearchRequestProperty(property));
    return this;
  }

  private AgentHistoryItemKeyFilterProperty toAgentHistoryItemKeyFilterProperty(
      final BasicStringFilterProperty src) {
    final AgentHistoryItemKeyFilterProperty dest = new AgentHistoryItemKeyFilterProperty();
    dest.set$Eq(src.get$Eq());
    dest.set$Neq(src.get$Neq());
    dest.set$Exists(src.get$Exists());
    dest.set$In(src.get$In());
    dest.set$NotIn(src.get$NotIn());
    return dest;
  }
}
