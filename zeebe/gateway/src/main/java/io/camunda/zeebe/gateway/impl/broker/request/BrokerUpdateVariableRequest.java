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

public class BrokerUpdateVariableRequest extends BrokerExecuteCommand<VariableRecord> {

  private final VariableRecord requestDto = new VariableRecord();

  public BrokerUpdateVariableRequest() {
    super(ValueType.VARIABLE, VariableIntent.UPDATE);
    super.request.setPartitionId(Protocol.START_PARTITION_ID);
  }

  public BrokerUpdateVariableRequest setKey(final long key) {
    super.request.setKey(key);
    return this;
  }

  public BrokerUpdateVariableRequest setValue(final DirectBuffer value) {
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
