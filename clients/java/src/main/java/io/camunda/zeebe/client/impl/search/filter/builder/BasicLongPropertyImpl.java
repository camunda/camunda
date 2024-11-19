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

import io.camunda.zeebe.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.BasicLongFilterProperty;
import java.util.List;

public class BasicLongPropertyImpl implements BasicLongProperty {
  private final BasicLongFilterProperty filterProperty = new BasicLongFilterProperty();

  @Override
  public BasicLongProperty eq(final Long value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public BasicLongProperty neq(final Long value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public BasicLongProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public BasicLongProperty in(final List<Long> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public BasicLongProperty in(final Long... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public BasicLongFilterProperty build() {
    return filterProperty;
  }
}
