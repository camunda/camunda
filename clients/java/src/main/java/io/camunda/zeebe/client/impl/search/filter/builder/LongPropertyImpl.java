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

import io.camunda.zeebe.client.api.search.filter.builder.LongProperty;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import java.util.List;

public class LongPropertyImpl implements LongProperty {
  private final LongFilterProperty filterProperty = new LongFilterProperty();

  @Override
  public LongProperty eq(final Long value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public LongProperty neq(final Long value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public LongProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public LongProperty in(final List<Long> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public LongProperty in(final Long... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public LongFilterProperty build() {
    return filterProperty;
  }

  @Override
  public LongProperty gt(final Long value) {
    filterProperty.set$Gt(value);
    return this;
  }

  @Override
  public LongProperty gte(final Long value) {
    filterProperty.set$Gte(value);
    return this;
  }

  @Override
  public LongProperty lt(final Long value) {
    filterProperty.set$Lt(value);
    return this;
  }

  @Override
  public LongProperty lte(final Long value) {
    filterProperty.set$Lte(value);
    return this;
  }
}
