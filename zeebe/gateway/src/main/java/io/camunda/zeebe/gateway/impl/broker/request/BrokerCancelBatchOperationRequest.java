/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementManagementRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import org.agrona.DirectBuffer;

public class BrokerCancelBatchOperationRequest
    extends BrokerExecuteCommand<BatchOperationLifecycleManagementManagementRecord> {

  BatchOperationLifecycleManagementManagementRecord requestDto =
      new BatchOperationLifecycleManagementManagementRecord();

  public BrokerCancelBatchOperationRequest() {
    super(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.CANCEL);
  }

  public BrokerCancelBatchOperationRequest setBatchOperationKey(final long batchOperationKey) {
    requestDto.setBatchOperationKey(batchOperationKey);
    return this;
  }

  @Override
  public BatchOperationLifecycleManagementManagementRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected BatchOperationLifecycleManagementManagementRecord toResponseDto(
      final DirectBuffer buffer) {
    final BatchOperationLifecycleManagementManagementRecord responseDto =
        new BatchOperationLifecycleManagementManagementRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
