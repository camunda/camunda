/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerClockResetRequest extends BrokerExecuteCommand<ClockRecord> {

  private final ClockRecord requestDto = new ClockRecord();

  public BrokerClockResetRequest() {
    super(ValueType.CLOCK, ClockIntent.RESET);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ClockRecord toResponseDto(final DirectBuffer buffer) {
    final ClockRecord responseDto = new ClockRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
