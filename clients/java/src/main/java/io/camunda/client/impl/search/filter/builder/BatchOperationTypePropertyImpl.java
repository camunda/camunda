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

import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.filter.builder.BatchOperationTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import io.camunda.client.protocol.rest.BatchOperationTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class BatchOperationTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationTypeFilterProperty>
    implements BatchOperationTypeProperty {

  private final BatchOperationTypeFilterProperty filterProperty =
      new BatchOperationTypeFilterProperty();

  @Override
  public BatchOperationTypeProperty eq(final BatchOperationType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, BatchOperationTypeEnum.class));
    return this;
  }

  @Override
  public BatchOperationTypeProperty neq(final BatchOperationType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, BatchOperationTypeEnum.class));
    return this;
  }

  @Override
  public BatchOperationTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public BatchOperationTypeProperty in(final List<BatchOperationType> values) {
    filterProperty.set$In(
        values.stream()
            .map(source -> EnumUtil.convert(source, BatchOperationTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public BatchOperationTypeProperty in(final BatchOperationType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected BatchOperationTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public BatchOperationTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
