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
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import org.agrona.DirectBuffer;

public class BrokerUserUpdateRequest extends BrokerExecuteCommand<UserRecord> {
  private final UserRecord requestDto = new UserRecord();

  public BrokerUserUpdateRequest() {
    super(ValueType.USER, UserIntent.UPDATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerUserUpdateRequest setUsername(final String username) {
    requestDto.setUsername(username);
    return this;
  }

  public BrokerUserUpdateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerUserUpdateRequest setEmail(final String email) {
    requestDto.setEmail(email);
    return this;
  }

  public BrokerUserUpdateRequest setPassword(final String password) {
    requestDto.setPassword(password);
    return this;
  }

  @Override
  public UserRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected UserRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new UserRecord();
    response.wrap(buffer);
    return response;
  }
}
