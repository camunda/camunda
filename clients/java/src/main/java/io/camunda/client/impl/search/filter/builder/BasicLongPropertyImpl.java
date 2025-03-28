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

import io.camunda.client.api.search.filter.BasicStringFilterProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.impl.util.CollectionUtil;
import java.util.List;
import java.util.stream.Collectors;

public class BasicLongPropertyImpl implements BasicLongProperty {
  private final io.camunda.client.protocol.rest.BasicStringFilterProperty filterProperty =
      new io.camunda.client.protocol.rest.BasicStringFilterProperty();

  @Override
  public BasicLongProperty eq(final Long value) {
    filterProperty.set$Eq(String.valueOf(value));
    return this;
  }

  @Override
  public BasicLongProperty neq(final Long value) {
    filterProperty.set$Neq(String.valueOf(value));
    return this;
  }

  @Override
  public BasicLongProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public BasicLongProperty in(final List<Long> values) {
    filterProperty.set$In(values.stream().map(String::valueOf).collect(Collectors.toList()));
    return this;
  }

  @Override
  public BasicLongProperty in(final Long... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public BasicStringFilterProperty build() {
    return ResponseMapper.fromProtocolObject(filterProperty);
  }

  @Override
  public BasicLongProperty nin(final List<Long> values) {
    filterProperty.set$Nin(values.stream().map(String::valueOf).collect(Collectors.toList()));
    return this;
  }

  @Override
  public BasicLongProperty nin(final Long... value) {
    return nin(CollectionUtil.toList(value));
  }
}
