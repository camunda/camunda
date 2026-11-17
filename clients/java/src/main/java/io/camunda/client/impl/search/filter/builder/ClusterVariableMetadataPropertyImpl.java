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
package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.filter.builder.ClusterVariableMetadataProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.protocol.rest.AdvancedMetadataValueFilter;
import java.math.BigDecimal;
import java.util.List;

public class ClusterVariableMetadataPropertyImpl
    extends TypedSearchRequestPropertyProvider<AdvancedMetadataValueFilter>
    implements ClusterVariableMetadataProperty {
  private final AdvancedMetadataValueFilter filterProperty = new AdvancedMetadataValueFilter();

  @Override
  public ClusterVariableMetadataProperty eq(final Object value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty neq(final Object value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty in(final List<Object> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty in(final Object... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public ClusterVariableMetadataProperty gt(final Number value) {
    filterProperty.set$Gt(toBigDecimal(value));
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty gte(final Number value) {
    filterProperty.set$Gte(toBigDecimal(value));
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty lt(final Number value) {
    filterProperty.set$Lt(toBigDecimal(value));
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty lte(final Number value) {
    filterProperty.set$Lte(toBigDecimal(value));
    return this;
  }

  @Override
  public ClusterVariableMetadataProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  protected AdvancedMetadataValueFilter getSearchRequestProperty() {
    return filterProperty;
  }

  private static BigDecimal toBigDecimal(final Number value) {
    return value == null ? null : new BigDecimal(value.toString());
  }
}
