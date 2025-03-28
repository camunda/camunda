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

import io.camunda.client.api.search.filter.DateTimeFilterProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.impl.util.CollectionUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class DateTimePropertyImpl implements DateTimeProperty {
  private final io.camunda.client.protocol.rest.DateTimeFilterProperty filterProperty =
      new io.camunda.client.protocol.rest.DateTimeFilterProperty();

  @Override
  public DateTimeProperty gt(final OffsetDateTime value) {
    filterProperty.set$Gt(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty gte(final OffsetDateTime value) {
    filterProperty.set$Gte(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty lt(final OffsetDateTime value) {
    filterProperty.set$Lt(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty lte(final OffsetDateTime value) {
    filterProperty.set$Lte(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty eq(final OffsetDateTime value) {
    filterProperty.set$Eq(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty neq(final OffsetDateTime value) {
    filterProperty.set$Neq(value.toString());
    return this;
  }

  @Override
  public DateTimeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public DateTimeProperty in(final List<OffsetDateTime> values) {
    filterProperty.set$In(
        values.stream().map(OffsetDateTime::toString).collect(Collectors.toList()));
    return this;
  }

  @Override
  public DateTimeProperty in(final OffsetDateTime... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public DateTimeFilterProperty build() {
    return ResponseMapper.fromProtocolObject(filterProperty);
  }
}
