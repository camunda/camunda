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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** A worker's answer to a probe, sent to the probe's partition. */
public final class BrokerProbeAnswerRequest extends BrokerExecuteCommand<ProbeRecord> {

  private final ProbeRecord requestDto = new ProbeRecord();

  public BrokerProbeAnswerRequest(final long probeKey) {
    super(ValueType.PROBE, ProbeIntent.ANSWER);
    request.setKey(probeKey);
    setPartitionId(Protocol.decodePartitionId(probeKey));
  }

  public BrokerProbeAnswerRequest setOutcome(final ProbeOutcome outcome) {
    requestDto.setOutcome(outcome);
    return this;
  }

  public BrokerProbeAnswerRequest setData(final Map<String, Object> data) {
    if (data != null && !data.isEmpty()) {
      requestDto.setData(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(data)));
    }
    return this;
  }

  public BrokerProbeAnswerRequest setMessage(final String message) {
    if (message != null) {
      requestDto.setMessage(message);
    }
    return this;
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
