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

import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.filter.builder.AuditLogEntityTypeFilterProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.EntityTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogEntityTypeFilterPropertyImpl
    extends TypedSearchRequestPropertyProvider<EntityTypeFilterProperty>
    implements AuditLogEntityTypeFilterProperty {

  private final EntityTypeFilterProperty filterProperty = new EntityTypeFilterProperty();

  @Override
  public AuditLogEntityTypeFilterProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  public AuditLogEntityTypeFilterProperty eq(final AuditLogEntityTypeEnum value) {
    filterProperty.set$Eq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogEntityTypeEnum.class));
    return this;
  }

  @Override
  public AuditLogEntityTypeFilterProperty neq(final AuditLogEntityTypeEnum value) {
    filterProperty.set$Neq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogEntityTypeEnum.class));
    return this;
  }

  @Override
  public AuditLogEntityTypeFilterProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AuditLogEntityTypeFilterProperty in(final List<AuditLogEntityTypeEnum> values) {
    filterProperty.set$In(
        values.stream()
            .map(
                source ->
                    EnumUtil.convert(
                        source, io.camunda.client.protocol.rest.AuditLogEntityTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AuditLogEntityTypeFilterProperty in(final AuditLogEntityTypeEnum... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected EntityTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
