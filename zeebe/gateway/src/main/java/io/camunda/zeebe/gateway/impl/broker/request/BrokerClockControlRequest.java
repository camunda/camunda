/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockControlRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockControlIntent;
import org.agrona.DirectBuffer;

public final class BrokerClockControlRequest extends BrokerExecuteCommand<ClockControlRecord> {

  private final ClockControlRecord clockControlDto = new ClockControlRecord();

  public BrokerClockControlRequest() {
    super(ValueType.CLOCK_CONTROL, ClockControlIntent.PIN);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerClockControlRequest setTime(final long time) {
    clockControlDto.setTime(time);

    return this;
  }

  public BrokerClockControlRequest setTenantId(final String tenantId) {
    clockControlDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public ClockControlRecord getRequestWriter() {
    return clockControlDto;
  }

  @Override
  protected ClockControlRecord toResponseDto(final DirectBuffer buffer) {
    final ClockControlRecord responseDto = new ClockControlRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
