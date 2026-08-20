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

import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.builder.IncidentStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.IncidentStateEnum;
import io.camunda.client.protocol.rest.IncidentStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class IncidentStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<IncidentStateFilterProperty>
    implements IncidentStateProperty {

  private final IncidentStateFilterProperty filterProperty = new IncidentStateFilterProperty();

  @Override
  public IncidentStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  public IncidentStateProperty eq(final IncidentState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, IncidentStateEnum.class));
    return this;
  }

  @Override
  public IncidentStateProperty neq(final IncidentState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, IncidentStateEnum.class));
    return this;
  }

  @Override
  public IncidentStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public IncidentStateProperty in(final List<IncidentState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, IncidentStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public IncidentStateProperty in(final IncidentState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public IncidentStateProperty notIn(final IncidentState... values) {
    return notIn(CollectionUtil.toList(values));
  }

  @Override
  public IncidentStateProperty notIn(final List<IncidentState> values) {
    filterProperty.set$NotIn(
        values.stream()
            .map(source -> (EnumUtil.convert(source, IncidentStateEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  protected IncidentStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
