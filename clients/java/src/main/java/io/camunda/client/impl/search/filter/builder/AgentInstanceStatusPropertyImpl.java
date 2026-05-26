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

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.filter.builder.AgentInstanceStatusProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentInstanceStatusEnum;
import io.camunda.client.protocol.rest.AgentInstanceStatusFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class AgentInstanceStatusPropertyImpl
    extends TypedSearchRequestPropertyProvider<AgentInstanceStatusFilterProperty>
    implements AgentInstanceStatusProperty {

  private final AgentInstanceStatusFilterProperty filterProperty =
      new AgentInstanceStatusFilterProperty();

  @Override
  public AgentInstanceStatusProperty eq(final AgentInstanceStatus value) {
    filterProperty.set$Eq(EnumUtil.convert(value, AgentInstanceStatusEnum.class));
    return this;
  }

  @Override
  public AgentInstanceStatusProperty neq(final AgentInstanceStatus value) {
    filterProperty.set$Neq(EnumUtil.convert(value, AgentInstanceStatusEnum.class));
    return this;
  }

  @Override
  public AgentInstanceStatusProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AgentInstanceStatusProperty in(final List<AgentInstanceStatus> values) {
    filterProperty.set$In(
        values.stream()
            .map(status -> EnumUtil.convert(status, AgentInstanceStatusEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AgentInstanceStatusProperty in(final AgentInstanceStatus... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected AgentInstanceStatusFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public AgentInstanceStatusProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
