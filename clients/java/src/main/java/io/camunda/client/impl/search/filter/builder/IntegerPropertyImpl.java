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

import io.camunda.client.api.search.filter.IntegerFilterProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.impl.util.CollectionUtil;
import java.util.List;

public class IntegerPropertyImpl implements IntegerProperty {
  private final io.camunda.client.protocol.rest.IntegerFilterProperty filterProperty =
      new io.camunda.client.protocol.rest.IntegerFilterProperty();

  @Override
  public IntegerProperty eq(final Integer value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public IntegerProperty neq(final Integer value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public IntegerProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public IntegerProperty in(final List<Integer> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public IntegerProperty in(final Integer... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public IntegerFilterProperty build() {
    return ResponseMapper.fromProtocolObject(filterProperty);
  }

  @Override
  public IntegerProperty gt(final Integer value) {
    filterProperty.set$Gt(value);
    return this;
  }

  @Override
  public IntegerProperty gte(final Integer value) {
    filterProperty.set$Gte(value);
    return this;
  }

  @Override
  public IntegerProperty lt(final Integer value) {
    filterProperty.set$Lt(value);
    return this;
  }

  @Override
  public IntegerProperty lte(final Integer value) {
    filterProperty.set$Lte(value);
    return this;
  }
}
