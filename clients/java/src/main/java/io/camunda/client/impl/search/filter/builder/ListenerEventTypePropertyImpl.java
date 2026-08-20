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

import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class ListenerEventTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<JobListenerEventTypeFilterProperty>
    implements ListenerEventTypeProperty {

  private final JobListenerEventTypeFilterProperty filterProperty =
      new JobListenerEventTypeFilterProperty();

  @Override
  public ListenerEventTypeProperty eq(final ListenerEventType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, JobListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public ListenerEventTypeProperty neq(final ListenerEventType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, JobListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public ListenerEventTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ListenerEventTypeProperty in(final List<ListenerEventType> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, JobListenerEventTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public ListenerEventTypeProperty in(final ListenerEventType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected JobListenerEventTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public ListenerEventTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
