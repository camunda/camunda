/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.role;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import org.agrona.DirectBuffer;

public class BrokerRoleCreateRequest extends BrokerExecuteCommand<RoleRecord> {

  private final RoleRecord requestDto = new RoleRecord();

  public BrokerRoleCreateRequest() {
    super(ValueType.ROLE, RoleIntent.CREATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerRoleCreateRequest setRoleId(final String roleId) {
    requestDto.setRoleId(roleId);
    return this;
  }

  public BrokerRoleCreateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerRoleCreateRequest setDescription(final String description) {
    requestDto.setDescription(description);
    return this;
  }

  @Override
  public RoleRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected RoleRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new RoleRecord();
    response.wrap(buffer);
    return response;
  }
}
