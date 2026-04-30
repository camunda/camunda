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

import io.camunda.client.api.search.filter.ResourceFilter;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.impl.search.filter.builder.BasicLongPropertyImpl;
import io.camunda.client.impl.search.filter.builder.IntegerPropertyImpl;
import io.camunda.client.impl.search.filter.builder.StringPropertyImpl;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import java.util.function.Consumer;

public class ResourceFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.ResourceFilter>
    implements ResourceFilter {

  private final io.camunda.client.protocol.rest.ResourceFilter filter;

  public ResourceFilterImpl() {
    filter = new io.camunda.client.protocol.rest.ResourceFilter();
  }

  @Override
  public ResourceFilter resourceKey(final long value) {
    return resourceKey(f -> f.eq(value));
  }

  @Override
  public ResourceFilter resourceKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setResourceKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter resourceName(final String value) {
    return resourceName(f -> f.eq(value));
  }

  @Override
  public ResourceFilter resourceName(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setResourceName(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter resourceId(final String value) {
    return resourceId(f -> f.eq(value));
  }

  @Override
  public ResourceFilter resourceId(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setResourceId(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter version(final int value) {
    return version(f -> f.eq(value));
  }

  @Override
  public ResourceFilter version(final Consumer<IntegerProperty> fn) {
    final IntegerProperty property = new IntegerPropertyImpl();
    fn.accept(property);
    filter.setVersion(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter versionTag(final String value) {
    return versionTag(f -> f.eq(value));
  }

  @Override
  public ResourceFilter versionTag(final Consumer<StringProperty> fn) {
    final StringProperty property = new StringPropertyImpl();
    fn.accept(property);
    filter.setVersionTag(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter deploymentKey(final long value) {
    return deploymentKey(f -> f.eq(value));
  }

  @Override
  public ResourceFilter deploymentKey(final Consumer<BasicLongProperty> fn) {
    final BasicLongProperty property = new BasicLongPropertyImpl();
    fn.accept(property);
    filter.setDeploymentKey(provideSearchRequestProperty(property));
    return this;
  }

  @Override
  public ResourceFilter tenantId(final String value) {
    filter.setTenantId(value);
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.ResourceFilter getSearchRequestProperty() {
    return filter;
  }
}
