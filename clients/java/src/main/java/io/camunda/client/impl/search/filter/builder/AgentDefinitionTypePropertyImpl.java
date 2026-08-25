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

import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.filter.builder.AgentDefinitionTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AgentDefinitionTypeEnum;
import io.camunda.client.protocol.rest.AgentDefinitionTypeFilterProperty;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class AgentDefinitionTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<AgentDefinitionTypeFilterProperty>
    implements AgentDefinitionTypeProperty {

  private final AgentDefinitionTypeFilterProperty filterProperty =
      new AgentDefinitionTypeFilterProperty();

  @Override
  public AgentDefinitionTypeProperty eq(final AgentDefinitionType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, AgentDefinitionTypeEnum.class));
    return this;
  }

  @Override
  public AgentDefinitionTypeProperty neq(final AgentDefinitionType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, AgentDefinitionTypeEnum.class));
    return this;
  }

  @Override
  public AgentDefinitionTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AgentDefinitionTypeProperty in(final List<AgentDefinitionType> values) {
    filterProperty.set$In(
        values.stream()
            .map(value -> EnumUtil.convert(value, AgentDefinitionTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AgentDefinitionTypeProperty in(final AgentDefinitionType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public AgentDefinitionTypeProperty notIn(final AgentDefinitionType... values) {
    return notIn(CollectionUtil.toList(values));
  }

  @Override
  public AgentDefinitionTypeProperty notIn(final Collection<AgentDefinitionType> values) {
    filterProperty.set$NotIn(
        values.stream()
            .map(value -> EnumUtil.convert(value, AgentDefinitionTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  protected AgentDefinitionTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public AgentDefinitionTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
