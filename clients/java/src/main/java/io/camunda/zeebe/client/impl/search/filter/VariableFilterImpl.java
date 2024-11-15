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
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.LongPropertyBuilderCallback;
import io.camunda.zeebe.client.api.search.filter.builder.PropertyBuilderCallbacks.StringPropertyBuilderCallback;
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.impl.search.filter.builder.LongPropertyBuilderImpl;
import io.camunda.zeebe.client.impl.search.filter.builder.StringPropertyBuilderImpl;
import io.camunda.zeebe.client.protocol.rest.VariableFilterRequest;

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
  public VariableFilter variableKey(final LongPropertyBuilderCallback callback) {
    filter.setVariableKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public VariableFilter value(final String value) {
    value(b -> b.eq(value));
    return this;
  }

  @Override
  public VariableFilter value(final StringPropertyBuilderCallback callback) {
    filter.setValue(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public VariableFilter name(final String name) {
    name(b -> b.eq(name));
    return this;
  }

  @Override
  public VariableFilter name(final StringPropertyBuilderCallback callback) {
    filter.setName(callback.apply(new StringPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public VariableFilter scopeKey(final Long scopeKey) {
    scopeKey(b -> b.eq(scopeKey));
    return this;
  }

  @Override
  public VariableFilter scopeKey(final LongPropertyBuilderCallback callback) {
    filter.setScopeKey(callback.apply(new LongPropertyBuilderImpl()).build());
    return this;
  }

  @Override
  public VariableFilter processInstanceKey(final Long processInstanceKey) {
    processInstanceKey(b -> b.eq(processInstanceKey));
    return this;
  }

  @Override
  public VariableFilter processInstanceKey(final LongPropertyBuilderCallback callback) {
    filter.setProcessInstanceKey(callback.apply(new LongPropertyBuilderImpl()).build());
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
