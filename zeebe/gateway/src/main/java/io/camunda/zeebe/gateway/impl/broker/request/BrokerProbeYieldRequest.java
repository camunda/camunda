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
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Hands a collected probe back to its partition when the gateway could not deliver it to the
 * worker; the probe becomes activatable again for the next poll.
 */
public final class BrokerProbeYieldRequest extends BrokerExecuteCommand<ProbeRecord> {

  private final ProbeRecord requestDto = new ProbeRecord();

  public BrokerProbeYieldRequest(final long probeKey) {
    super(ValueType.PROBE, ProbeIntent.YIELD);
    request.setKey(probeKey);
    setPartitionId(Protocol.decodePartitionId(probeKey));
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProbeRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ProbeRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
