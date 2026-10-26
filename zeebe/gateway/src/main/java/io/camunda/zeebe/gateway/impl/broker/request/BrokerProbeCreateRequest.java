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
import io.camunda.zeebe.protocol.record.value.ProbeOperation;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Creates a probe and waits for its end: the response is the worker's answer (ANSWERED), or why the
 * probe ended without one (TIMED_OUT, CANCELED). No partition is set, so the request goes to any
 * partition.
 */
public final class BrokerProbeCreateRequest extends BrokerExecuteCommand<ProbeRecord> {

  private final ProbeRecord requestDto = new ProbeRecord();

  public BrokerProbeCreateRequest() {
    super(ValueType.PROBE, ProbeIntent.CREATE);
  }

  public BrokerProbeCreateRequest setOperation(final ProbeOperation operation) {
    requestDto.setOperation(operation);
    return this;
  }

  public BrokerProbeCreateRequest setTemplate(final String template) {
    requestDto.setTemplate(template);
    return this;
  }

  public BrokerProbeCreateRequest setInputExpression(final String inputExpression) {
    requestDto.setInputExpression(inputExpression);
    return this;
  }

  public BrokerProbeCreateRequest setTenantId(final String tenantId) {
    if (tenantId != null) {
      requestDto.setTenantId(tenantId);
    }
    return this;
  }

  public BrokerProbeCreateRequest setRequestTimeout(final long requestTimeout) {
    requestDto.setRequestTimeout(requestTimeout);
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
