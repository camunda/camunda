/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import org.agrona.DirectBuffer;

public class BrokerResumeBatchOperationRequest
    extends BrokerExecuteCommand<BatchOperationLifecycleManagementRecord> {

  BatchOperationLifecycleManagementRecord requestDto =
      new BatchOperationLifecycleManagementRecord();

  public BrokerResumeBatchOperationRequest() {
    super(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationIntent.RESUME);
  }

  public BrokerResumeBatchOperationRequest setBatchOperationKey(final long batchOperationKey) {
    requestDto.setBatchOperationKey(batchOperationKey);
    return this;
  }

  @Override
  public BatchOperationLifecycleManagementRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected BatchOperationLifecycleManagementRecord toResponseDto(final DirectBuffer buffer) {
    final BatchOperationLifecycleManagementRecord responseDto =
        new BatchOperationLifecycleManagementRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
