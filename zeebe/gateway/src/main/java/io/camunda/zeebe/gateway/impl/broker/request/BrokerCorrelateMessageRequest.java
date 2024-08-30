/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.RequestDispatchStrategy;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.impl.broker.PublishMessageDispatchStrategy;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BrokerCorrelateMessageRequest
    extends BrokerExecuteCommand<MessageCorrelationRecord> {

  private final MessageCorrelationRecord requestDto = new MessageCorrelationRecord();
  private final PublishMessageDispatchStrategy dispatchStrategy;

  public BrokerCorrelateMessageRequest(final String messageName, final String correlationKey) {
    super(ValueType.MESSAGE_CORRELATION, MessageCorrelationIntent.CORRELATE);
    requestDto.setName(messageName).setCorrelationKey(correlationKey);
    dispatchStrategy = new PublishMessageDispatchStrategy(correlationKey);
  }

  public BrokerCorrelateMessageRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerCorrelateMessageRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected MessageCorrelationRecord toResponseDto(final DirectBuffer buffer) {
    final MessageCorrelationRecord responseDto = new MessageCorrelationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  @Override
  public Optional<RequestDispatchStrategy> requestDispatchStrategy() {
    return Optional.of(dispatchStrategy);
  }
}
