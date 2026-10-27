/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.scaling;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class GetScaleUpProgress extends BrokerExecuteCommand<ScaleRecord> {

  private final ScaleRecord requestDto = new ScaleRecord();

  public GetScaleUpProgress() {
    super(ValueType.SCALE, ScaleIntent.STATUS);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public GetScaleUpProgress(final int partitionId) {
    super(ValueType.SCALE, ScaleIntent.STATUS);
    setPartitionId(partitionId);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ScaleRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ScaleRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
