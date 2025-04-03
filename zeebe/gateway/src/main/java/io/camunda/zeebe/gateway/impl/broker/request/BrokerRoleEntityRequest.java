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
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.agrona.DirectBuffer;

public final class BrokerRoleEntityRequest extends BrokerExecuteCommand<RoleRecord> {
  private final RoleRecord roleDto = new RoleRecord();

  private BrokerRoleEntityRequest(final RoleIntent intent) {
    super(ValueType.ROLE, intent);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public static BrokerRoleEntityRequest createAddRequest() {
    return new BrokerRoleEntityRequest(RoleIntent.ADD_ENTITY);
  }

  public static BrokerRoleEntityRequest createRemoveRequest() {
    return new BrokerRoleEntityRequest(RoleIntent.REMOVE_ENTITY);
  }

  public BrokerRoleEntityRequest setRoleId(final String roleId) {
    roleDto.setRoleId(roleId);
    return this;
  }

  public BrokerRoleEntityRequest setEntity(final EntityType entityType, final String entityId) {
    if (entityType != EntityType.USER && entityType != EntityType.MAPPING) {
      throw new IllegalArgumentException(
          "For now, roles can only be granted to users and mappings");
    }
    roleDto.setEntityType(entityType);
    roleDto.setEntityId(entityId);
    return this;
  }

  @Override
  public RoleRecord getRequestWriter() {
    return roleDto;
  }

  @Override
  protected RoleRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new RoleRecord();
    response.wrap(buffer);
    return response;
  }
}
