/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Hands a collected probe back to the partition that owns it, when the gateway could not deliver it
 * to the worker: the probe becomes activatable again for the next poll (D41).
 */
@NullMarked
public final class BrokerProbeYieldRequest extends BrokerExecuteCommand<ProbeRecord> {

  private final ProbeRecord requestDto = new ProbeRecord();

  public BrokerProbeYieldRequest(final long probeKey) {
    super(ValueType.PROBE, ProbeIntent.YIELD);
    request.setKey(probeKey);
  }

  @Override
  public ProbeRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProbeRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ProbeRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
