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
package io.camunda.zeebe.client.impl.search.filter.builder;

import io.camunda.zeebe.client.api.search.filter.builder.DateTimePropertyBuilder;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.DateTimeFilterProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class DateTimePropertyBuilderImpl implements DateTimePropertyBuilder {
  private final DateTimeFilterProperty filterProperty = new DateTimeFilterProperty();

  @Override
  public DateTimePropertyBuilder gt(final OffsetDateTime value) {
    filterProperty.set$Gt(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder gte(final OffsetDateTime value) {
    filterProperty.set$Gte(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder lt(final OffsetDateTime value) {
    filterProperty.set$Lt(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder lte(final OffsetDateTime value) {
    filterProperty.set$Lte(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder eq(final OffsetDateTime value) {
    filterProperty.set$Eq(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder neq(final OffsetDateTime value) {
    filterProperty.set$Neq(value.toString());
    return this;
  }

  @Override
  public DateTimePropertyBuilder exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public DateTimePropertyBuilder in(final List<OffsetDateTime> values) {
    filterProperty.set$In(
        values.stream().map(OffsetDateTime::toString).collect(Collectors.toList()));
    return this;
  }

  @Override
  public DateTimePropertyBuilder in(final OffsetDateTime... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public DateTimeFilterProperty build() {
    return filterProperty;
  }
}
