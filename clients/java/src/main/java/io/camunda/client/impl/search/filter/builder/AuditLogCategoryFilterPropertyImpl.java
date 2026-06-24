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

import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.filter.builder.AuditLogCategoryFilterProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.CategoryFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogCategoryFilterPropertyImpl
    extends TypedSearchRequestPropertyProvider<CategoryFilterProperty>
    implements AuditLogCategoryFilterProperty {

  private final CategoryFilterProperty filterProperty = new CategoryFilterProperty();

  @Override
  public AuditLogCategoryFilterProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }

  @Override
  public AuditLogCategoryFilterProperty eq(final AuditLogCategoryEnum value) {
    filterProperty.set$Eq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogCategoryEnum.class));
    return this;
  }

  @Override
  public AuditLogCategoryFilterProperty neq(final AuditLogCategoryEnum value) {
    filterProperty.set$Neq(
        EnumUtil.convert(value, io.camunda.client.protocol.rest.AuditLogCategoryEnum.class));
    return this;
  }

  @Override
  public AuditLogCategoryFilterProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public AuditLogCategoryFilterProperty in(final List<AuditLogCategoryEnum> values) {
    filterProperty.set$In(
        values.stream()
            .map(
                source ->
                    EnumUtil.convert(
                        source, io.camunda.client.protocol.rest.AuditLogCategoryEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public AuditLogCategoryFilterProperty in(final AuditLogCategoryEnum... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected CategoryFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }
}
