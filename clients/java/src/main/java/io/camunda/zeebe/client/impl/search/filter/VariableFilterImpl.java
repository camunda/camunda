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
import io.camunda.zeebe.client.impl.search.TypedSearchRequestPropertyProvider;
import io.camunda.zeebe.client.protocol.rest.VariableFilterRequest;

public class VariableFilterImpl extends TypedSearchRequestPropertyProvider<VariableFilterRequest>
    implements VariableFilter {

  private final VariableFilterRequest filter;

  public VariableFilterImpl(final VariableFilterRequest filter) {
    this.filter = filter;
  }

  public VariableFilterImpl() {
    filter = new VariableFilterRequest();
  }

  @Override
  public VariableFilter variableKey(final Long key) {
    filter.setVariableKey(key);
    return this;
  }

  @Override
  public VariableFilter value(final String value) {
    filter.setValue(value);
    return this;
  }

  @Override
  public VariableFilter name(final String name) {
    filter.setName(name);
    return this;
  }

  @Override
  public VariableFilter scopeKey(final Long scopeKey) {
    filter.setScopeKey(scopeKey);
    return this;
  }

  @Override
  public VariableFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(processInstanceKey);
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
