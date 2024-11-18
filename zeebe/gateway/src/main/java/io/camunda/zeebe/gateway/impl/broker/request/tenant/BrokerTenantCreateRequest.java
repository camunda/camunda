/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request.tenant;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import org.agrona.DirectBuffer;

public class BrokerTenantCreateRequest extends BrokerExecuteCommand<TenantRecord> {

  private final TenantRecord requestDto = new TenantRecord();

  public BrokerTenantCreateRequest() {
    super(ValueType.TENANT, TenantIntent.CREATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerTenantCreateRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  public BrokerTenantCreateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  @Override
  public TenantRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected TenantRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new TenantRecord();
    response.wrap(buffer);
    return response;
  }
}
