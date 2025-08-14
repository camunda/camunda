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
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import org.agrona.DirectBuffer;

public final class BrokerCreateGlobalVariableRequest extends BrokerExecuteCommand<VariableRecord> {

  private final VariableRecord requestDto = new VariableRecord();

  public BrokerCreateGlobalVariableRequest() {
    super(ValueType.VARIABLE, VariableIntent.CREATE);
    super.request.setPartitionId(Protocol.START_PARTITION_ID);
    requestDto.setScopeKey(-1);
    requestDto.setProcessInstanceKey(-1);
    requestDto.setProcessDefinitionKey(-1);
  }

  public BrokerCreateGlobalVariableRequest setVariables(
      final String name, final DirectBuffer value) {
    requestDto.setName(name);
    requestDto.setValue(value);
    return this;
  }

  @Override
  public VariableRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected VariableRecord toResponseDto(final DirectBuffer buffer) {
    final VariableRecord responseDto = new VariableRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
