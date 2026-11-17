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

import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.filter.builder.AgentInstanceHistoryRoleProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class AgentInstanceHistoryRolePropertyImpl
    extends TypedSearchRequestPropertyProvider<AgentInstanceHistoryRoleFilterProperty>
    implements AgentInstanceHistoryRoleProperty {

  private final AgentInstanceHistoryRoleFilterProperty filterProperty =
      new AgentInstanceHistoryRoleFilterProperty();

  @Override
  public AgentInstanceHistoryRoleProperty eq(final AgentInstanceHistoryRole value) {
    filterProperty.set$Eq(EnumUtil.convert(value, AgentInstanceHistoryRoleEnum.class));
    return this;
  }

  @Override
  public AgentInstanceHistoryRoleProperty neq(final AgentInstanceHistoryRole value) {
    filterProperty.set$Neq(EnumUtil.convert(value, AgentInstanceHistoryRoleEnum.class));
    return this;
  }

  @Override
  public AgentInstanceHistoryRoleProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AgentInstanceHistoryRoleProperty in(final List<AgentInstanceHistoryRole> values) {
    filterProperty.set$In(
        values.stream()
            .map(role -> EnumUtil.convert(role, AgentInstanceHistoryRoleEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AgentInstanceHistoryRoleProperty in(final AgentInstanceHistoryRole... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected AgentInstanceHistoryRoleFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
