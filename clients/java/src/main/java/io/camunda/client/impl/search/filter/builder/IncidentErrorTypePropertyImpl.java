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

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.filter.builder.IncidentErrorTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.IncidentErrorTypeEnum;
import io.camunda.client.protocol.rest.IncidentErrorTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class IncidentErrorTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<IncidentErrorTypeFilterProperty>
    implements IncidentErrorTypeProperty {

  private final IncidentErrorTypeFilterProperty filterProperty =
      new IncidentErrorTypeFilterProperty();

  @Override
  public IncidentErrorTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  public IncidentErrorTypeProperty eq(final IncidentErrorType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, IncidentErrorTypeEnum.class));
    return this;
  }

  @Override
  public IncidentErrorTypeProperty neq(final IncidentErrorType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, IncidentErrorTypeEnum.class));
    return this;
  }

  @Override
  public IncidentErrorTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public IncidentErrorTypeProperty in(final List<IncidentErrorType> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> (EnumUtil.convert(source, IncidentErrorTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public IncidentErrorTypeProperty in(final IncidentErrorType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public IncidentErrorTypeProperty notIn(final List<IncidentErrorType> values) {
    filterProperty.set$NotIn(
        values.stream()
            .map(source -> (EnumUtil.convert(source, IncidentErrorTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public IncidentErrorTypeProperty notIn(final IncidentErrorType... values) {
    return notIn(CollectionUtil.toList(values));
  }

  @Override
  protected IncidentErrorTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
