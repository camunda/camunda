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

import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.filter.builder.BatchOperationItemStateProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AdvancedBatchOperationItemStateFilter;
import io.camunda.client.protocol.rest.BatchOperationItemStateEnum;
import io.camunda.client.protocol.rest.BatchOperationItemStateFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class BatchOperationItemStatePropertyImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationItemStateFilterProperty>
    implements BatchOperationItemStateProperty {

  private final AdvancedBatchOperationItemStateFilter filterProperty =
      new AdvancedBatchOperationItemStateFilter();

  @Override
  public BatchOperationItemStateProperty eq(final BatchOperationItemState value) {
    filterProperty.set$Eq(EnumUtil.convert(value, BatchOperationItemStateEnum.class));
    return this;
  }

  @Override
  public BatchOperationItemStateProperty neq(final BatchOperationItemState value) {
    filterProperty.set$Neq(EnumUtil.convert(value, BatchOperationItemStateEnum.class));
    return this;
  }

  @Override
  public BatchOperationItemStateProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public BatchOperationItemStateProperty in(final List<BatchOperationItemState> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> EnumUtil.convert(source, BatchOperationItemStateEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public BatchOperationItemStateProperty in(final BatchOperationItemState... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected BatchOperationItemStateFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public BatchOperationItemStateProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
