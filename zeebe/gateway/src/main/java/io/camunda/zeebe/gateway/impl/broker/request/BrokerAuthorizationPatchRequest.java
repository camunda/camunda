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
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerAuthorizationPatchRequest extends BrokerExecuteCommand<AuthorizationRecord> {
  private final AuthorizationRecord requestDto = new AuthorizationRecord();

  public BrokerAuthorizationPatchRequest() {
    super(ValueType.AUTHORIZATION, AuthorizationIntent.UPDATE_PERMISSION);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerAuthorizationPatchRequest setOwnerKey(final Long ownerKey) {
    requestDto.setOwnerKey(ownerKey);
    return this;
  }

  public BrokerAuthorizationPatchRequest setAction(final PermissionAction action) {
    requestDto.setAction(action);
    return this;
  }

  public BrokerAuthorizationPatchRequest setResourceType(
      final AuthorizationResourceType resourceType) {
    requestDto.setResourceType(resourceType);
    return this;
  }

  public BrokerAuthorizationPatchRequest addPermissions(
      final PermissionType permissionType, final List<String> resourceIds) {
    requestDto.addPermission(
        new Permission().setPermissionType(permissionType).addResourceIds(resourceIds));
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
