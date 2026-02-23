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

import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.api.search.filter.builder.GlobalTaskListenerEventTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalTaskListenerEventTypeEnum;
import io.camunda.client.protocol.rest.GlobalTaskListenerEventTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalTaskListenerEventTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<GlobalTaskListenerEventTypeFilterProperty>
    implements GlobalTaskListenerEventTypeProperty {

  private final GlobalTaskListenerEventTypeFilterProperty filterProperty =
      new GlobalTaskListenerEventTypeFilterProperty();

  @Override
  public GlobalTaskListenerEventTypeProperty eq(final GlobalTaskListenerEventType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, GlobalTaskListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public GlobalTaskListenerEventTypeProperty neq(final GlobalTaskListenerEventType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, GlobalTaskListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public GlobalTaskListenerEventTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public GlobalTaskListenerEventTypeProperty in(final List<GlobalTaskListenerEventType> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, GlobalTaskListenerEventTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public GlobalTaskListenerEventTypeProperty in(final GlobalTaskListenerEventType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected GlobalTaskListenerEventTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public GlobalTaskListenerEventTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
