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
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import org.agrona.DirectBuffer;

public class BrokerMappingRuleUpdateRequest extends BrokerExecuteCommand<MappingRuleRecord> {

  private final MappingRuleRecord requestDto = new MappingRuleRecord();

  public BrokerMappingRuleUpdateRequest() {
    super(ValueType.MAPPING_RULE, MappingRuleIntent.UPDATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerMappingRuleUpdateRequest setClaimName(final String claimName) {
    requestDto.setClaimName(claimName);
    return this;
  }

  public BrokerMappingRuleUpdateRequest setClaimValue(final String claimValue) {
    requestDto.setClaimValue(claimValue);
    return this;
  }

  public BrokerMappingRuleUpdateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerMappingRuleUpdateRequest setMappingRuleId(final String mappingRuleId) {
    requestDto.setMappingRuleId(mappingRuleId);
    return this;
  }

  @Override
  public MappingRuleRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected MappingRuleRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new MappingRuleRecord();
    response.wrap(buffer);
    return response;
  }
}
