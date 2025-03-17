/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.search.filter.FilterBase;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.agrona.DirectBuffer;

public class BrokerCreateBatchOperationRequest
    extends BrokerExecuteCommand<BatchOperationCreationRecord> {

  BatchOperationCreationRecord requestDto = new BatchOperationCreationRecord();

  public BrokerCreateBatchOperationRequest() {
    super(ValueType.BATCH_OPERATION, BatchOperationIntent.CREATE);
  }

  public BrokerCreateBatchOperationRequest setBatchOperationType(
      final BatchOperationType batchOperationType) {
    requestDto.setBatchOperationType(batchOperationType);
    return this;
  }

  @Override
  public BatchOperationCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected BatchOperationCreationRecord toResponseDto(final DirectBuffer buffer) {
    final BatchOperationCreationRecord responseDto = new BatchOperationCreationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  public BrokerCreateBatchOperationRequest setFilter(final FilterBase filter) {
    requestDto.setFilter(filter);
    return this;
  }
}
