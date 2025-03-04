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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.BatchOperationState;
import io.camunda.client.api.search.response.BatchOperationType;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationResponse;
import java.util.ArrayList;
import java.util.List;

public class BatchOperationImpl implements BatchOperation {

  private final Long batchOperationKey;
  private final BatchOperationType type;
  private final BatchOperationState state;
  private final String startDate;
  private final String endDate;
  private final Integer operationsTotalCount;
  private final Integer operationsFailedCount;
  private final Integer operationsCompletedCount;
  private final List<Long> keys = new ArrayList<>();

  public BatchOperationImpl(final BatchOperationCreatedResult item) {
    batchOperationKey = ParseUtil.parseLongOrNull(item.getBatchOperationKey());
    type = BatchOperationType.valueOf(item.getBatchOperationType());
    state = null;
    startDate = null;
    endDate = null;
    operationsTotalCount = null;
    operationsFailedCount = null;
    operationsCompletedCount = null;
  }

  public BatchOperationImpl(final BatchOperationResponse item) {
    batchOperationKey = ParseUtil.parseLongOrNull(item.getBatchOperationKey());
    type = BatchOperationType.valueOf(item.getBatchOperationType());
    state = BatchOperationState.valueOf(item.getState());
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    operationsTotalCount = item.getOperationsTotalCount();
    operationsFailedCount = item.getOperationsFailedCount();
    operationsCompletedCount = item.getOperationsCompletedCount();
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKey;
  }

  @Override
  public List<Long> keys() {
    return keys;
  }

  @Override
  public BatchOperationState getState() {
    return state;
  }

  @Override
  public BatchOperationType getType() {
    return type;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }

  @Override
  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  @Override
  public Integer getOperationsFailedCount() {
    return operationsFailedCount;
  }

  @Override
  public Integer getOperationsCompletedCount() {
    return operationsCompletedCount;
  }
}
