/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerAuthorizationCreateRequest extends BrokerExecuteCommand<AuthorizationRecord> {
  private final AuthorizationRecord requestDto = new AuthorizationRecord();

  public BrokerAuthorizationCreateRequest() {
    super(ValueType.AUTHORIZATION, AuthorizationIntent.CREATE);
  }

  public BrokerAuthorizationCreateRequest setOwnerKey(final String ownerKey) {
    requestDto.setOwnerKey(ownerKey);
    return this;
  }

  public BrokerAuthorizationCreateRequest setOwnerType(final AuthorizationOwnerType ownerType) {
    requestDto.setOwnerType(ownerType);
    return this;
  }

  public BrokerAuthorizationCreateRequest setResourceKey(final String resourceKey) {
    requestDto.setResourceKey(resourceKey);
    return this;
  }

  public BrokerAuthorizationCreateRequest setResourceType(final String resourceType) {
    requestDto.setResourceType(resourceType);
    return this;
  }

  public BrokerAuthorizationCreateRequest setPermissions(final List<String> permissions) {
    requestDto.setPermissions(permissions);
    return this;
  }

  @Override
  public AuthorizationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected AuthorizationRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new AuthorizationRecord();
    response.wrap(buffer);
    return response;
  }
}
