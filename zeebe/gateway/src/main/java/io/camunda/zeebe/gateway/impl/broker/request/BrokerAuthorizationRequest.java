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
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Set;
import org.agrona.DirectBuffer;

public class BrokerAuthorizationRequest extends BrokerExecuteCommand<AuthorizationRecord> {
  private final AuthorizationRecord requestDto = new AuthorizationRecord();

  public BrokerAuthorizationRequest(final AuthorizationIntent intent) {
    super(ValueType.AUTHORIZATION, intent);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerAuthorizationRequest setAuthorizationKey(final long authorizationKey) {
    requestDto.setAuthorizationKey(authorizationKey);
    return this;
  }

  public BrokerAuthorizationRequest setOwnerId(final String ownerId) {
    requestDto.setOwnerId(ownerId);
    return this;
  }

  public BrokerAuthorizationRequest setOwnerType(final AuthorizationOwnerType ownerType) {
    requestDto.setOwnerType(ownerType);
    return this;
  }

  public BrokerAuthorizationRequest setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    requestDto.setResourceMatcher(resourceMatcher);
    return this;
  }

  public BrokerAuthorizationRequest setResourceId(final String resourceId) {
    requestDto.setResourceId(resourceId);
    return this;
  }

  public BrokerAuthorizationRequest setResourceType(final AuthorizationResourceType resourceType) {
    requestDto.setResourceType(resourceType);
    return this;
  }

  public BrokerAuthorizationRequest setPermissionTypes(final Set<PermissionType> permissionTypes) {
    requestDto.setPermissionTypes(permissionTypes);
    return this;
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
