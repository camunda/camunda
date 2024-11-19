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
package io.camunda.zeebe.client.impl.search.filter;

import io.camunda.zeebe.client.api.search.filter.VariableFilter;
import io.camunda.zeebe.client.api.search.filter.builder.LongProperty;
import io.camunda.zeebe.client.api.search.filter.builder.StringProperty;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.filter.builder.LongPropertyImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.zeebe.client.protocol.rest.VariableFilterRequest;
import java.util.function.Consumer;

public class VariableFilterImpl extends TypedSearchRequestPropertyProvider<VariableFilterRequest>
    implements VariableFilter {

  private final VariableFilterRequest filter;

  public VariableFilterImpl() {
    filter = new VariableFilterRequest();
  }

  @Override
  public VariableFilter variableKey(final Long key) {
    variableKey(b -> b.eq(key));
    return this;
  }

  @Override
  public VariableFilter variableKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setVariableKey(property.build());
    return this;
  }

  @Override
  public VariableFilter value(final String value) {
    value(b -> b.eq(value));
    return this;
  }

  @Override
  public VariableFilter value(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setValue(property.build());
    return this;
  }

  @Override
  public VariableFilter name(final String name) {
    name(b -> b.eq(name));
    return this;
  }

  @Override
  public VariableFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(property.build());
    return this;
  }

  @Override
  public VariableFilter scopeKey(final Long scopeKey) {
    scopeKey(b -> b.eq(scopeKey));
    return this;
  }

  @Override
  public VariableFilter scopeKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setScopeKey(property.build());
    return this;
  }

  @Override
  public VariableFilter processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public VariableFilter processInstanceKey(final Consumer<LongProperty> fn) {
    final LongProperty property = new LongPropertyImpl();
    fn.accept(property);
    filter.setProcessInstanceKey(property.build());
    return this;
  }

  @Override
  public VariableFilter tenantId(final String tenantId) {
    filter.setTenantId(tenantId);
    return this;
  }

  @Override
  public VariableFilter isTruncated(final Boolean isTruncated) {
    filter.isTruncated(isTruncated);
    return this;
  }

  @Override
  protected VariableFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
