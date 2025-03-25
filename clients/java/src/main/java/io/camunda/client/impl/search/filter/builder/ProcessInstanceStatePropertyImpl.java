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

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceStateFilterProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.api.search.response.ProcessInstanceState;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessInstanceStatePropertyImpl implements ProcessInstanceStateProperty {
  private final ProcessInstanceStateFilterProperty filterProperty =
      new ProcessInstanceStateFilterProperty();

  @Override
  public ProcessInstanceStateProperty eq(final ProcessInstanceState value) {
    filterProperty.setEq(ProcessInstanceState.toProtocolState(value));
    return this;
  }

  @Override
  public ProcessInstanceStateProperty neq(final ProcessInstanceState value) {
    filterProperty.setNeq(ProcessInstanceState.toProtocolState(value));
    return this;
  }

  @Override
  public ProcessInstanceStateProperty exists(final boolean value) {
    filterProperty.setExists(value);
    return this;
  }

  @Override
  public ProcessInstanceStateProperty in(final List<ProcessInstanceState> values) {
    filterProperty.setIn(
        values.stream().map(ProcessInstanceState::toProtocolState).collect(Collectors.toList()));
    return this;
  }

  @Override
  public ProcessInstanceStateProperty in(final ProcessInstanceState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public ProcessInstanceStateFilterProperty build() {
    return filterProperty;
  }

  @Override
  public ProcessInstanceStateProperty like(final String value) {
    filterProperty.setLike(value);
    return this;
  }
}
