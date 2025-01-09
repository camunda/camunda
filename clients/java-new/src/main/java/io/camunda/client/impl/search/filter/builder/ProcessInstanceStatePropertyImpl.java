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

import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty;
import java.util.List;

public class ProcessInstanceStatePropertyImpl implements ProcessInstanceStateProperty {
  private final ProcessInstanceStateFilterProperty filterProperty =
      new ProcessInstanceStateFilterProperty();

  @Override
  public ProcessInstanceStateProperty eq(final ProcessInstanceStateEnum value) {
    filterProperty.set$Eq(value);
    return this;
  }

  @Override
  public ProcessInstanceStateProperty neq(final ProcessInstanceStateEnum value) {
    filterProperty.set$Neq(value);
    return this;
  }

  @Override
  public ProcessInstanceStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ProcessInstanceStateFilterProperty build() {
    return filterProperty;
  }

  @Override
  public ProcessInstanceStateProperty in(final List<ProcessInstanceStateEnum> values) {
    filterProperty.set$In(values);
    return this;
  }

  @Override
  public ProcessInstanceStateProperty in(final ProcessInstanceStateEnum... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  public ProcessInstanceStateProperty like(final String value) {
    filterProperty.$like(value);
    return this;
  }
}
