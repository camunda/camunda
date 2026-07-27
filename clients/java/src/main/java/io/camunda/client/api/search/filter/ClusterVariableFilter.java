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

import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.filter.builder.ClusterVariableKindProperty;
import io.camunda.client.api.search.filter.builder.ClusterVariableMetadataProperty;
import io.camunda.client.api.search.filter.builder.ClusterVariableScopeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.Map;
import java.util.function.Consumer;

public interface ClusterVariableFilter extends SearchRequestFilter {

  /**
   * Filters cluster variables by the specified value.
   *
   * @param value the value of the variable
   * @return the updated filter
   */
  ClusterVariableFilter value(final String value);

  /**
   * Filters cluster variables by the specified value using {@link StringProperty} consumer.
   *
   * @param fn the value {@link StringProperty} consumer of the variable
   * @return the updated filter
   */
  ClusterVariableFilter value(final Consumer<StringProperty> fn);

  /**
   * Filters cluster variables by the specified name.
   *
   * @param name the name of the variable
   * @return the updated filter
   */
  ClusterVariableFilter name(final String name);

  /**
   * Filters cluster variables by the specified name using {@link StringProperty} consumer.
   *
   * @param fn the name {@link StringProperty} consumer of the variable
   * @return the updated filter
   */
  ClusterVariableFilter name(final Consumer<StringProperty> fn);

  /**
   * Filters cluster variables by the specified tenant id.
   *
   * @param tenantId the tenant id of the variable
   * @return the updated filter
   */
  ClusterVariableFilter tenantId(final String tenantId);

  /**
   * Filters cluster variables by the specified tenantId using {@link StringProperty} consumer.
   *
   * @param fn the tenantId {@link StringProperty} consumer of the variable
   * @return the updated filter
   */
  ClusterVariableFilter tenantId(Consumer<StringProperty> fn);

  /**
   * Filters cluster variables by the specified scope.
   *
   * @param scope the scope of the variable (GLOBAL or TENANT)
   * @return the updated filter
   */
  ClusterVariableFilter scope(final ClusterVariableScope scope);

  /**
   * Filters cluster variables by the specified scope using {@link ClusterVariableScope} consumer.
   *
   * @param fn the scope {@link ClusterVariableScope} consumer of the variable
   * @return the updated filter
   */
  ClusterVariableFilter scope(final Consumer<ClusterVariableScopeProperty> fn);

  /**
   * Filters cluster variables by truncation status in storage. When true, returns only variables
   * whose stored values are truncated (i.e., their values exceed the storage size limit and are not
   * fully persisted). When false, returns only variables with non-truncated stored values. This
   * filter is based on the underlying storage characteristic, not the response format.
   *
   * @param isTruncated true to filter for truncated values, false for non-truncated values
   * @return the updated filter
   */
  ClusterVariableFilter isTruncated(final Boolean isTruncated);

  /**
   * Filters cluster variables by metadata entries, matching each key on equality with the given
   * value.
   *
   * @param metadata a map of metadata key to the value it must equal
   * @return the updated filter
   */
  ClusterVariableFilter metadata(final Map<String, Object> metadata);

  /**
   * Filters cluster variables by an advanced filter on the value of a single metadata key.
   *
   * @param key the metadata key to filter on
   * @param fn the {@link ClusterVariableMetadataProperty} consumer describing the filter
   * @return the updated filter
   */
  ClusterVariableFilter metadata(
      final String key, final Consumer<ClusterVariableMetadataProperty> fn);

  /**
   * Filters cluster variables by the specified kind.
   *
   * @param kind the kind of the cluster variable (JSON or SECRET_REFERENCE)
   * @return the updated filter
   */
  ClusterVariableFilter kind(final ClusterVariableKind kind);

  /**
   * Filters cluster variables by the specified kind using {@link ClusterVariableKindProperty}
   * consumer.
   *
   * @param fn the kind {@link ClusterVariableKindProperty} consumer of the variable
   * @return the updated filter
   */
  ClusterVariableFilter kind(final Consumer<ClusterVariableKindProperty> fn);
}
