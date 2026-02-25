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

import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.filter.builder.GlobalListenerSourceProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalListenerSourceEnum;
import io.camunda.client.protocol.rest.GlobalListenerSourceFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalListenerSourcePropertyImpl
    extends TypedSearchRequestPropertyProvider<GlobalListenerSourceFilterProperty>
    implements GlobalListenerSourceProperty {

  private final GlobalListenerSourceFilterProperty filterProperty =
      new GlobalListenerSourceFilterProperty();

  @Override
  public GlobalListenerSourceProperty eq(final GlobalListenerSource value) {
    filterProperty.set$Eq(EnumUtil.convert(value, GlobalListenerSourceEnum.class));
    return this;
  }

  @Override
  public GlobalListenerSourceProperty neq(final GlobalListenerSource value) {
    filterProperty.set$Neq(EnumUtil.convert(value, GlobalListenerSourceEnum.class));
    return this;
  }

  @Override
  public GlobalListenerSourceProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public GlobalListenerSourceProperty in(final List<GlobalListenerSource> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, GlobalListenerSourceEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public GlobalListenerSourceProperty in(final GlobalListenerSource... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected GlobalListenerSourceFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public GlobalListenerSourceProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
