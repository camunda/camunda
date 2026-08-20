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

import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.filter.builder.ClusterVariableScopeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ClusterVariableScopeEnum;
import io.camunda.client.protocol.rest.ClusterVariableScopeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class ClusterVariableScopePropertyImpl
    extends TypedSearchRequestPropertyProvider<ClusterVariableScopeFilterProperty>
    implements ClusterVariableScopeProperty {

  private final ClusterVariableScopeFilterProperty filterProperty =
      new ClusterVariableScopeFilterProperty();

  @Override
  public ClusterVariableScopeProperty eq(final ClusterVariableScope value) {
    filterProperty.set$Eq(EnumUtil.convert(value, ClusterVariableScopeEnum.class));
    return this;
  }

  @Override
  public ClusterVariableScopeProperty neq(final ClusterVariableScope value) {
    filterProperty.set$Neq(EnumUtil.convert(value, ClusterVariableScopeEnum.class));
    return this;
  }

  @Override
  public ClusterVariableScopeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ClusterVariableScopeProperty in(final List<ClusterVariableScope> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, ClusterVariableScopeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public ClusterVariableScopeProperty in(final ClusterVariableScope... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected ClusterVariableScopeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public ClusterVariableScopeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
