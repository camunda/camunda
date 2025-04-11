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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.filter.ElementInstanceStateFilterProperty;
import io.camunda.client.api.search.filter.builder.ElementInstanceStateProperty;
import io.camunda.client.impl.util.CollectionUtil;
import java.util.List;

public class ElementInstanceStatePropertyImpl implements ElementInstanceStateProperty {
  private final ElementInstanceStateFilterProperty filterProperty =
      new ElementInstanceStateFilterProperty();

  @Override
  public ElementInstanceStateProperty eq(final ElementInstanceState value) {
    filterProperty.setEq(value);
    return this;
  }

  @Override
  public ElementInstanceStateProperty neq(final ElementInstanceState value) {
    filterProperty.setNeq(value);
    return this;
  }

  @Override
  public ElementInstanceStateProperty exists(final boolean value) {
    filterProperty.setExists(value);
    return this;
  }

  @Override
  public ElementInstanceStateProperty in(final List<ElementInstanceState> values) {
    filterProperty.setIn(values);
    return this;
  }

  @Override
  public ElementInstanceStateProperty in(final ElementInstanceState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public ElementInstanceStateFilterProperty build() {
    return filterProperty;
  }

  @Override
  public ElementInstanceStateProperty like(final String value) {
    filterProperty.setLike(value);
    return this;
  }
}
