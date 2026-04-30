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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface ResourceFilter extends SearchRequestFilter {

  /**
   * Filter by resource key.
   *
   * @param value the resource key
   * @return the builder for chaining
   */
  ResourceFilter resourceKey(final long value);

  /**
   * Filter by resource key using a {@link BasicLongProperty} consumer for advanced filtering.
   *
   * @param fn the {@link BasicLongProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter resourceKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filter by resource name.
   *
   * @param value the resource name
   * @return the builder for chaining
   */
  ResourceFilter resourceName(final String value);

  /**
   * Filter by resource name using a {@link StringProperty} consumer for advanced filtering.
   *
   * @param fn the {@link StringProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter resourceName(final Consumer<StringProperty> fn);

  /**
   * Filter by resource ID.
   *
   * @param value the resource ID
   * @return the builder for chaining
   */
  ResourceFilter resourceId(final String value);

  /**
   * Filter by resource ID using a {@link StringProperty} consumer for advanced filtering.
   *
   * @param fn the {@link StringProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter resourceId(final Consumer<StringProperty> fn);

  /**
   * Filter by version.
   *
   * @param value the version
   * @return the builder for chaining
   */
  ResourceFilter version(final int value);

  /**
   * Filter by version using a {@link IntegerProperty} consumer for advanced filtering.
   *
   * @param fn the {@link IntegerProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter version(final Consumer<IntegerProperty> fn);

  /**
   * Filter by version tag.
   *
   * @param value the version tag
   * @return the builder for chaining
   */
  ResourceFilter versionTag(final String value);

  /**
   * Filter by version tag using a {@link StringProperty} consumer for advanced filtering.
   *
   * @param fn the {@link StringProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter versionTag(final Consumer<StringProperty> fn);

  /**
   * Filter by deployment key.
   *
   * @param value the deployment key
   * @return the builder for chaining
   */
  ResourceFilter deploymentKey(final long value);

  /**
   * Filter by deployment key using a {@link BasicLongProperty} consumer for advanced filtering.
   *
   * @param fn the {@link BasicLongProperty} consumer
   * @return the builder for chaining
   */
  ResourceFilter deploymentKey(final Consumer<BasicLongProperty> fn);

  /**
   * Filter by tenant ID.
   *
   * @param value the tenant ID
   * @return the builder for chaining
   */
  ResourceFilter tenantId(final String value);
}
