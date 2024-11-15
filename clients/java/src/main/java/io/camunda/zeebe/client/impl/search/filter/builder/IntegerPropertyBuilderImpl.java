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

import io.camunda.zeebe.client.api.search.filter.builder.IntegerPropertyBuilder;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.IntegerFilterProperty;
import java.util.List;

public class IntegerPropertyBuilderImpl implements IntegerPropertyBuilder {
  private final IntegerFilterProperty filterProperty = new IntegerFilterProperty();

  @Override
  public IntegerPropertyBuilder eq(final Integer value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder neq(final Integer value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder in(final List<Integer> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public IntegerPropertyBuilder in(final Integer... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public IntegerFilterProperty build() {
    return filterProperty;
  }

  @Override
  public IntegerPropertyBuilder gt(final Integer value) {
    filterProperty.set$Gt(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder gte(final Integer value) {
    filterProperty.set$Gte(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder lt(final Integer value) {
    filterProperty.set$Lt(value);
    return this;
  }

  @Override
  public IntegerPropertyBuilder lte(final Integer value) {
    filterProperty.set$Lte(value);
    return this;
  }
}
