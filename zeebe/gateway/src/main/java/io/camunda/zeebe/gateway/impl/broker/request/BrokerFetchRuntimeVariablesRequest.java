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
import io.camunda.zeebe.protocol.impl.record.value.runtimevariables.RuntimeVariablesRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RuntimeVariablesIntent;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import org.agrona.DirectBuffer;

public final class BrokerFetchRuntimeVariablesRequest
    extends BrokerExecuteCommand<RuntimeVariablesRecord> {

  private final RuntimeVariablesRecord requestDto = new RuntimeVariablesRecord();

  public BrokerFetchRuntimeVariablesRequest() {
    super(ValueType.RUNTIME_VARIABLES, RuntimeVariablesIntent.FETCH);
  }

  public BrokerFetchRuntimeVariablesRequest setScopeKey(final long scopeKey) {
    requestDto.setScopeKey(scopeKey);
    setPartitionId(Protocol.decodePartitionId(scopeKey));
    return this;
  }

  public BrokerFetchRuntimeVariablesRequest setScope(final RuntimeVariableScope scope) {
    requestDto.setScope(scope);
    return this;
  }

  @Override
  public RuntimeVariablesRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected RuntimeVariablesRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new RuntimeVariablesRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
