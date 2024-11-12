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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BrokerPublishMessageRequest extends BrokerExecuteCommand<MessageRecord> {

  private final MessageRecord requestDto = new MessageRecord();
  private final PublishMessageDispatchStrategy dispatchStrategy;

  public BrokerPublishMessageRequest(final String messageName, final String correlationKey) {
    super(ValueType.MESSAGE, MessageIntent.PUBLISH);
    requestDto.setName(messageName).setCorrelationKey(correlationKey);
    dispatchStrategy = new PublishMessageDispatchStrategy(correlationKey);
  }

  public DirectBuffer getCorrelationKey() {
    return requestDto.getCorrelationKeyBuffer();
  }

  public BrokerPublishMessageRequest setMessageId(final String messageId) {
    requestDto.setMessageId(messageId);
    return this;
  }

  public BrokerPublishMessageRequest setTimeToLive(final long timeToLive) {
    requestDto.setTimeToLive(timeToLive);
    return this;
  }

  public BrokerPublishMessageRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerPublishMessageRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  @Override
  public MessageRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected MessageRecord toResponseDto(final DirectBuffer buffer) {
    final MessageRecord responseDto = new MessageRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  @Override
  public Optional<RequestDispatchStrategy> requestDispatchStrategy() {
    return Optional.of(dispatchStrategy);
  }
}
