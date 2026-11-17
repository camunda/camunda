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

import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.filter.builder.WaitStateElementTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.WaitStateElementTypeEnum;
import io.camunda.client.protocol.rest.WaitStateElementTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class WaitStateElementTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<WaitStateElementTypeFilterProperty>
    implements WaitStateElementTypeProperty {

  private final WaitStateElementTypeFilterProperty filterProperty =
      new WaitStateElementTypeFilterProperty();

  @Override
  public WaitStateElementTypeProperty eq(final WaitStateElementType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, WaitStateElementTypeEnum.class));
    return this;
  }

  @Override
  public WaitStateElementTypeProperty neq(final WaitStateElementType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, WaitStateElementTypeEnum.class));
    return this;
  }

  @Override
  public WaitStateElementTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public WaitStateElementTypeProperty in(final List<WaitStateElementType> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> EnumUtil.convert(source, WaitStateElementTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public WaitStateElementTypeProperty in(final WaitStateElementType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected WaitStateElementTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
