/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.MessageIntent;
import org.agrona.DirectBuffer;

public class BrokerPublishMessageRequest extends BrokerExecuteCommand<Void> {

  private final MessageRecord requestDto = new MessageRecord();

  public BrokerPublishMessageRequest(String messageName, String correlationKey) {
    super(ValueType.MESSAGE, MessageIntent.PUBLISH);
    requestDto.setName(messageName).setCorrelationKey(correlationKey);
  }

  public DirectBuffer getCorrelationKey() {
    return requestDto.getCorrelationKeyBuffer();
  }

  public BrokerPublishMessageRequest setMessageId(String messageId) {
    requestDto.setMessageId(messageId);
    return this;
  }

  public BrokerPublishMessageRequest setTimeToLive(long timeToLive) {
    requestDto.setTimeToLive(timeToLive);
    return this;
  }

  public BrokerPublishMessageRequest setVariables(DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public MessageRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected Void toResponseDto(DirectBuffer buffer) {
    return null;
  }
}
