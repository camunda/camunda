/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerDeleteHistoryRequest extends BrokerExecuteCommand<HistoryDeletionRecord> {

  private final HistoryDeletionRecord requestDto = new HistoryDeletionRecord();

  public BrokerDeleteHistoryRequest() {
    super(ValueType.HISTORY_DELETION, HistoryDeletionIntent.DELETE);
  }

  public BrokerDeleteHistoryRequest setResourceKey(final long resourceKey) {
    requestDto.setResourceKey(resourceKey);
    return this;
  }

  public BrokerDeleteHistoryRequest setResourceType(final HistoryDeletionType resourceType) {
    requestDto.setResourceType(resourceType);
    return this;
  }

  public BrokerDeleteHistoryRequest setProcessId(final String processId) {
    requestDto.setProcessId(processId);
    return this;
  }

  public BrokerDeleteHistoryRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  public BrokerDeleteHistoryRequest setDecisionDefinitionId(final String decisionDefinitionId) {
    requestDto.setDecisionDefinitionId(decisionDefinitionId);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected HistoryDeletionRecord toResponseDto(final DirectBuffer buffer) {
    final HistoryDeletionRecord responseDto = new HistoryDeletionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
