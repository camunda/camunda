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

import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.filter.FlowNodeInstanceStateFilterProperty;
import io.camunda.client.api.search.filter.builder.FlowNodeInstanceStateProperty;
import io.camunda.client.impl.util.CollectionUtil;
import java.util.List;

public class FlowNodeInstanceStatePropertyImpl implements FlowNodeInstanceStateProperty {
  private final FlowNodeInstanceStateFilterProperty filterProperty =
      new FlowNodeInstanceStateFilterProperty();

  @Override
  public FlowNodeInstanceStateProperty eq(final FlowNodeInstanceState value) {
    filterProperty.setEq(value);
    return this;
  }

  @Override
  public FlowNodeInstanceStateProperty neq(final FlowNodeInstanceState value) {
    filterProperty.setNeq(value);
    return this;
  }

  @Override
  public FlowNodeInstanceStateProperty exists(final boolean value) {
    filterProperty.setExists(value);
    return this;
  }

  @Override
  public FlowNodeInstanceStateProperty in(final List<FlowNodeInstanceState> values) {
    filterProperty.setIn(values);
    return this;
  }

  @Override
  public FlowNodeInstanceStateProperty in(final FlowNodeInstanceState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public FlowNodeInstanceStateFilterProperty build() {
    return filterProperty;
  }

  @Override
  public FlowNodeInstanceStateProperty like(final String value) {
    filterProperty.setLike(value);
    return this;
  }
}
