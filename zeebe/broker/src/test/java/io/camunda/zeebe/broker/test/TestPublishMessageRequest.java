/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import org.agrona.DirectBuffer;

/**
 * Publishes a message through the broker client, without a gateway. The request goes to the default
 * physical tenant. The broker client dispatches it round-robin, which suits tests with a single
 * partition.
 */
public final class TestPublishMessageRequest extends BrokerExecuteCommand<MessageRecord> {

  private final MessageRecord requestDto = new MessageRecord();

  public TestPublishMessageRequest(final String messageName, final String correlationKey) {
    super(ValueType.MESSAGE, MessageIntent.PUBLISH);
    requestDto.setName(messageName).setCorrelationKey(correlationKey).setTimeToLive(0);
    setPartitionGroup(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  public TestPublishMessageRequest setMessageId(final String messageId) {
    requestDto.setMessageId(messageId);
    return this;
  }

  public TestPublishMessageRequest setTimeToLive(final long timeToLive) {
    requestDto.setTimeToLive(timeToLive);
    return this;
  }

  @Override
  public MessageRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected MessageRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new MessageRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
