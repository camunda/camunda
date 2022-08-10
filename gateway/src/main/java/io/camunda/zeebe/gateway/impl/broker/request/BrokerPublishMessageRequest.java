/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import org.agrona.DirectBuffer;

public final class BrokerPublishMessageRequest extends BrokerExecuteCommand<Void> {

  private final MessageRecord requestDto = new MessageRecord();

  public BrokerPublishMessageRequest(final String messageName, final String correlationKey) {
    this(messageName, correlationKey, RecordValueWithTenant.DEFAULT_TENANT_ID);
  }

  public BrokerPublishMessageRequest(
      final String messageName, final String correlationKey, final String tenantId) {
    super(ValueType.MESSAGE, MessageIntent.PUBLISH);
    requestDto.setName(messageName).setCorrelationKey(correlationKey);
    requestDto.setTenantId(tenantId);
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
    if (!tenantId.isEmpty()) {
      requestDto.setTenantId(tenantId);
    }
    return this;
  }

  @Override
  public MessageRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected Void toResponseDto(final DirectBuffer buffer) {
    return null;
  }
}
