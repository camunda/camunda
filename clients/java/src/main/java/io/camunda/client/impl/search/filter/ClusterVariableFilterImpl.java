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

import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.filter.ClusterVariableFilter;
import io.camunda.client.api.search.filter.builder.ClusterVariableScopeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.ClusterVariableScopePropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.ClusterVariableSearchQueryFilterRequest;
import java.util.function.Consumer;

public class ClusterVariableFilterImpl
    extends TypedSearchRequestPropertyProvider<ClusterVariableSearchQueryFilterRequest>
    implements ClusterVariableFilter {

  private final ClusterVariableSearchQueryFilterRequest filter;

  public ClusterVariableFilterImpl() {
    filter = new ClusterVariableSearchQueryFilterRequest();
  }

  @Override
  public ClusterVariableFilter value(final String value) {
    value(b -> b.eq(value));
    return this;
  }

  @Override
  public ClusterVariableFilter value(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setValue(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ClusterVariableFilter name(final String name) {
    name(b -> b.eq(name));
    return this;
  }

  @Override
  public ClusterVariableFilter name(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ClusterVariableFilter tenantId(final String tenantId) {
    tenantId(b -> b.eq(tenantId));
    return this;
  }

  @Override
  public ClusterVariableFilter tenantId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setTenantId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ClusterVariableFilter scope(final ClusterVariableScope scope) {
    scope(b -> b.eq(scope));
    return this;
  }

  @Override
  public ClusterVariableFilter scope(final Consumer<ClusterVariableScopeProperty> fn) {
    final ClusterVariableScopeProperty property = new ClusterVariableScopePropertyImpl();
    fn.accept(property);
    filter.setScope(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ClusterVariableFilter isTruncated(final Boolean isTruncated) {
    filter.setIsTruncated(isTruncated);
    return this;
  }

  @Override
  protected ClusterVariableSearchQueryFilterRequest getSearchRequestProperty() {
    return filter;
  }
}
