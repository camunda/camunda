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

  public BrokerTriggerTimerRequest(final long processInstanceKey, final String elementId) {
    super(ValueType.TIMER, TimerIntent.TRIGGER);
    // Route the command to the partition owning the process instance (encoded in the record key).
    request.setKey(processInstanceKey);
    // The caller addresses the held timer by (processInstanceKey, elementId); the engine resolves
    // the concrete (elementInstanceKey, timerKey) from its held-timer index and hydrates the
    // remaining fields (process definition, due date, ...). They have no msgpack default, so they
    // are seeded here purely to satisfy serialization.
    requestDto
        .setProcessInstanceKey(processInstanceKey)
        .setTargetElementId(BufferUtil.wrapString(elementId))
        .setElementInstanceKey(-1L)
        .setProcessDefinitionKey(-1L)
        .setDueDate(-1L)
        .setRepetitions(0);
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
