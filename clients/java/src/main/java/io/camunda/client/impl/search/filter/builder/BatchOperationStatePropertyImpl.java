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

import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.filter.builder.BatchOperationStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AdvancedBatchOperationStateFilter;
import io.camunda.client.protocol.rest.BatchOperationStateEnum;
import io.camunda.client.protocol.rest.BatchOperationStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class BatchOperationStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationStateFilterProperty>
    implements BatchOperationStateProperty {

  private final AdvancedBatchOperationStateFilter filterProperty =
      new AdvancedBatchOperationStateFilter();

  @Override
  public BatchOperationStateProperty eq(final BatchOperationState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, BatchOperationStateEnum.class));
    return this;
  }

  @Override
  public BatchOperationStateProperty neq(final BatchOperationState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, BatchOperationStateEnum.class));
    return this;
  }

  @Override
  public BatchOperationStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public BatchOperationStateProperty in(final List<BatchOperationState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> EnumUtil.convert(source, BatchOperationStateEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public BatchOperationStateProperty in(final BatchOperationState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected BatchOperationStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public BatchOperationStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
