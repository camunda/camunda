/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerRelocationStatusRequest extends BrokerExecuteCommand<ScaleRecord> {

  private final ScaleRecord requestDto = new ScaleRecord();

  public BrokerRelocationStatusRequest() {
    super(ValueType.SCALE, ScaleIntent.RELOCATION_STATUS);
    request.setPartitionId(1);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ScaleRecord toResponseDto(final DirectBuffer buffer) {
    final ScaleRecord responseDto = new ScaleRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
