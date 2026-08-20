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

import io.camunda.client.api.search.filter.builder.DecisionInstanceStateProperty;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.client.protocol.rest.DecisionInstanceStateFilterProperty;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DecisionInstanceStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<DecisionInstanceStateFilterProperty>
    implements DecisionInstanceStateProperty {
  private final DecisionInstanceStateFilterProperty filterProperty =
      new DecisionInstanceStateFilterProperty();

  @Override
  public DecisionInstanceStateProperty eq(final DecisionInstanceState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, DecisionInstanceStateEnum.class));
    return this;
  }

  @Override
  public DecisionInstanceStateProperty neq(final DecisionInstanceState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, DecisionInstanceStateEnum.class));
    return this;
  }

  @Override
  public DecisionInstanceStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public DecisionInstanceStateProperty in(final List<DecisionInstanceState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, DecisionInstanceStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public DecisionInstanceStateProperty in(final DecisionInstanceState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public DecisionInstanceStateProperty notIn(final DecisionInstanceState... values) {
    return notIn(CollectionUtil.toList(values));
  }

  @Override
  public DecisionInstanceStateProperty notIn(final Collection<DecisionInstanceState> values) {
    filterProperty.set$NotIn(
        values.stream()
            .map(source -> (EnumUtil.convert(source, DecisionInstanceStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  protected DecisionInstanceStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public DecisionInstanceStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
