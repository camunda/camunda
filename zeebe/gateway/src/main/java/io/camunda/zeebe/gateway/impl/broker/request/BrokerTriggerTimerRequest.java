/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class BrokerTriggerTimerRequest extends BrokerExecuteCommand<TimerRecord> {

  private final TimerRecord requestDto = new TimerRecord();

  private BrokerTriggerTimerRequest(final long timerKey) {
    super(ValueType.TIMER, TimerIntent.TRIGGER);
    // Route the command to the partition owning the timer (encoded in the timer key).
    request.setKey(timerKey);
    // The caller addresses the timer by its key; the engine hydrates the remaining fields from the
    // stored timer. They have no msgpack default, so they are seeded here purely to satisfy
    // serialization.
    requestDto
        .setProcessInstanceKey(-1L)
        .setTargetElementId(BufferUtil.wrapString(""))
        .setElementInstanceKey(-1L)
        .setProcessDefinitionKey(-1L)
        .setDueDate(-1L)
        .setRepetitions(0);
  }

  public static BrokerTriggerTimerRequest byTimerKey(final long timerKey) {
    return new BrokerTriggerTimerRequest(timerKey);
  }

  @Override
  public TimerRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected TimerRecord toResponseDto(final DirectBuffer buffer) {
    final TimerRecord responseDto = new TimerRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
