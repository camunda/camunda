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
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import org.agrona.DirectBuffer;

public class BrokerMappingUpdateRequest extends BrokerExecuteCommand<MappingRecord> {

  private final MappingRecord requestDto = new MappingRecord();

  public BrokerMappingUpdateRequest() {
    super(ValueType.MAPPING, MappingIntent.UPDATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerMappingUpdateRequest setClaimName(final String claimName) {
    requestDto.setClaimName(claimName);
    return this;
  }

  public BrokerMappingUpdateRequest setClaimValue(final String claimValue) {
    requestDto.setClaimValue(claimValue);
    return this;
  }

  public BrokerMappingUpdateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerMappingUpdateRequest setMappingId(final String id) {
    requestDto.setMappingId(id);
    return this;
  }

  @Override
  public MappingRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected MappingRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new MappingRecord();
    response.wrap(buffer);
    return response;
  }
}
