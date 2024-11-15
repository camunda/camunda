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

import io.camunda.zeebe.client.api.search.filter.builder.StringPropertyBuilder;
import io.camunda.zeebe.client.impl.util.CollectionUtil;
import io.camunda.zeebe.client.protocol.rest.StringFilterProperty;
import java.util.List;

public class StringPropertyBuilderImpl implements StringPropertyBuilder {
  private final StringFilterProperty filterProperty = new StringFilterProperty();

  @Override
  public StringPropertyBuilder eq(final String value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public StringPropertyBuilder neq(final String value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public StringPropertyBuilder exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public StringFilterProperty build() {
    return filterProperty;
  }

  @Override
  public StringPropertyBuilder in(final List<String> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public StringPropertyBuilder in(final String... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public StringPropertyBuilder like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
