/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerAuthorizationDeleteRequest extends BrokerExecuteCommand<AuthorizationRecord> {
  private final AuthorizationRecord requestDto = new AuthorizationRecord();

  public BrokerAuthorizationDeleteRequest(final long authorizationKey) {
    super(ValueType.AUTHORIZATION, AuthorizationIntent.DELETE);
    request.setKey(authorizationKey);
    requestDto.setAuthorizationKey(authorizationKey);
    requestDto.setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AuthorizationRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new AuthorizationRecord();
    response.wrap(buffer);
    return response;
  }
}
