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

import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.BatchOperationError;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BatchOperationImpl implements BatchOperation {

  private final String batchOperationKey;
  private final BatchOperationType type;
  private final BatchOperationState status;
  private final String startDate;
  private final String endDate;
  private final Integer operationsTotalCount;
  private final Integer operationsFailedCount;
  private final Integer operationsCompletedCount;
  private final List<Long> keys = new ArrayList<>();
  private final List<BatchOperationError> errors = new ArrayList<>();

  public BatchOperationImpl(final BatchOperationCreatedResult item) {
    batchOperationKey = item.getBatchOperationKey();
    type =
        item.getBatchOperationType() != null
            ? BatchOperationType.valueOf(item.getBatchOperationType().name())
            : null;
    status = null;
    startDate = null;
    endDate = null;
    operationsTotalCount = null;
    operationsFailedCount = null;
    operationsCompletedCount = null;
  }

  public BatchOperationImpl(final BatchOperationResponse item) {
    batchOperationKey = item.getBatchOperationKey();
    type =
        item.getBatchOperationType() != null
            ? BatchOperationType.valueOf(item.getBatchOperationType().name())
            : null;
    status = item.getState() != null ? BatchOperationState.valueOf(item.getState().name()) : null;
    startDate = item.getStartDate();
    endDate = item.getEndDate();
    operationsTotalCount = item.getOperationsTotalCount();
    operationsFailedCount = item.getOperationsFailedCount();
    operationsCompletedCount = item.getOperationsCompletedCount();

    if (item.getErrors() != null && !item.getErrors().isEmpty()) {
      errors.addAll(
          item.getErrors().stream().map(BatchOperationErrorImpl::new).collect(Collectors.toList()));
    }
  }

  @Override
  public String getBatchOperationKey() {
    return batchOperationKey;
  }

  @Override
  public BatchOperationState getStatus() {
    return status;
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

  @Override
  public List<BatchOperationError> getErrors() {
    return new ArrayList<>(errors);
  }
}
