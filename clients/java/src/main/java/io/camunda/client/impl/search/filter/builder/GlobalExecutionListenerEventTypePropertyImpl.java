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

import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import io.camunda.client.api.search.filter.builder.GlobalExecutionListenerEventTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalExecutionListenerEventTypeEnum;
import io.camunda.client.protocol.rest.GlobalExecutionListenerEventTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalExecutionListenerEventTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<GlobalExecutionListenerEventTypeFilterProperty>
    implements GlobalExecutionListenerEventTypeProperty {

  private final GlobalExecutionListenerEventTypeFilterProperty filterProperty =
      new GlobalExecutionListenerEventTypeFilterProperty();

  @Override
  public GlobalExecutionListenerEventTypeProperty eq(final GlobalExecutionListenerEventType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, GlobalExecutionListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public GlobalExecutionListenerEventTypeProperty neq(
      final GlobalExecutionListenerEventType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, GlobalExecutionListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public GlobalExecutionListenerEventTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public GlobalExecutionListenerEventTypeProperty in(
      final List<GlobalExecutionListenerEventType> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, GlobalExecutionListenerEventTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public GlobalExecutionListenerEventTypeProperty in(
      final GlobalExecutionListenerEventType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected GlobalExecutionListenerEventTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public GlobalExecutionListenerEventTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
