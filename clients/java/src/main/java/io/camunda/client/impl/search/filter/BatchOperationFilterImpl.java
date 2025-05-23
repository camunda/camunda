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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.BatchOperationFilter;
import io.camunda.client.protocol.rest.BatchOperationFilter.StateEnum;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;

public class BatchOperationFilterImpl
    extends TypedSearchRequestPropertyProvider<BatchOperationFilter>
    implements io.camunda.client.api.search.filter.BatchOperationFilter {

  private final BatchOperationFilter filter;

  public BatchOperationFilterImpl() {
    filter = new BatchOperationFilter();
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter batchOperationId(
      final String batchOperationId) {
    filter.setBatchOperationId(batchOperationId);
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter operationType(
      final BatchOperationType operationType) {
    if (operationType == null) {
      filter.setOperationType(null);
      return this;
    }

    filter.setOperationType(BatchOperationTypeEnum.valueOf(operationType.name()));
    return this;
  }

  @Override
  public io.camunda.client.api.search.filter.BatchOperationFilter state(
      final BatchOperationState state) {
    if (state == null) {
      filter.setState(null);
      return this;
    }

    filter.setState(StateEnum.valueOf(state.name()));
    return this;
  }

  @Override
  protected BatchOperationFilter getSearchRequestProperty() {
    return filter;
  }
}
