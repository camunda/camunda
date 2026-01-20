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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;

public class CreateBatchOperationResponseImpl implements CreateBatchOperationResponse {

  private final String batchOperationKey;
  private final BatchOperationType batchOperationType;

  public CreateBatchOperationResponseImpl(final BatchOperationCreatedResult response) {
    batchOperationKey = response.getBatchOperationKey();
    batchOperationType =
        EnumUtil.convert(response.getBatchOperationType(), BatchOperationType.class);
  }

  public CreateBatchOperationResponseImpl(
      final io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BatchOperationCreatedResult
          grpcResponse) {
    batchOperationKey = grpcResponse.getBatchOperationKey();
    batchOperationType =
        EnumUtil.convert(grpcResponse.getBatchOperationType(), BatchOperationType.class);
  }

  @Override
  public String getBatchOperationKey() {
    return batchOperationKey;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationType;
  }
}
