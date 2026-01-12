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

import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.filter.builder.AuditLogActorTypeFilterProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogActorTypeFilterPropertyImpl
    extends TypedSearchRequestPropertyProvider<
        io.camunda.client.protocol.rest.AuditLogActorTypeFilterProperty>
    implements AuditLogActorTypeFilterProperty {

  private final io.camunda.client.protocol.rest.AuditLogActorTypeFilterProperty filterProperty =
      new io.camunda.client.protocol.rest.AuditLogActorTypeFilterProperty();

  @Override
  public AuditLogActorTypeFilterProperty like(final String value) {
    return this;
  }

  @Override
  public AuditLogActorTypeFilterProperty eq(final AuditLogActorTypeEnum value) {
    filterProperty.set$Eq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogActorTypeEnum.class));
    return this;
  }

  @Override
  public AuditLogActorTypeFilterProperty neq(final AuditLogActorTypeEnum value) {
    filterProperty.set$Neq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogActorTypeEnum.class));
    return this;
  }

  @Override
  public AuditLogActorTypeFilterProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AuditLogActorTypeFilterProperty in(final List<AuditLogActorTypeEnum> values) {
    filterProperty.set$In(
        values.stream()
            .map(
                source ->
                    EnumUtil.convert(
                        source, io.camunda.client.protocol.rest.AuditLogActorTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AuditLogActorTypeFilterProperty in(final AuditLogActorTypeEnum... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected io.camunda.client.protocol.rest.AuditLogActorTypeFilterProperty
      getSearchRequestProperty() {
    return filterProperty;
  }
}
