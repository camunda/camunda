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

import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.filter.builder.WaitStateTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.WaitStateTypeEnum;
import io.camunda.client.protocol.rest.WaitStateTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class WaitStateTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<WaitStateTypeFilterProperty>
    implements WaitStateTypeProperty {

  private final WaitStateTypeFilterProperty filterProperty = new WaitStateTypeFilterProperty();

  @Override
  public WaitStateTypeProperty eq(final WaitStateType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, WaitStateTypeEnum.class));
    return this;
  }

  @Override
  public WaitStateTypeProperty neq(final WaitStateType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, WaitStateTypeEnum.class));
    return this;
  }

  @Override
  public WaitStateTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public WaitStateTypeProperty in(final List<WaitStateType> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> EnumUtil.convert(source, WaitStateTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public WaitStateTypeProperty in(final WaitStateType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected WaitStateTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
