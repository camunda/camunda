/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import org.agrona.DirectBuffer;

public final class BrokerBroadcastSignalRequest extends BrokerExecuteCommand<SignalRecord> {

  private final SignalRecord requestDto = new SignalRecord();

  public BrokerBroadcastSignalRequest(final String signalName) {
    super(ValueType.SIGNAL, SignalIntent.BROADCAST);
    requestDto.setSignalName(signalName);
  }

  public BrokerBroadcastSignalRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public SignalRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected SignalRecord toResponseDto(final DirectBuffer buffer) {
    final SignalRecord responseDto = new SignalRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
