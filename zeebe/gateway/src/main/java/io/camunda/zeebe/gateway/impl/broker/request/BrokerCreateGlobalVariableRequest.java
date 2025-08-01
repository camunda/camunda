/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.variable.GlobalVariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalVariableIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerCreateGlobalVariableRequest extends BrokerExecuteCommand<GlobalVariableRecord> {
  private final GlobalVariableRecord requestDto = new GlobalVariableRecord();

  public BrokerCreateGlobalVariableRequest() {
    super(ValueType.GLOBAL_VARIABLE, GlobalVariableIntent.CREATE);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected GlobalVariableRecord toResponseDto(final DirectBuffer buffer) {
    final GlobalVariableRecord responseDto = new GlobalVariableRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  public BrokerCreateGlobalVariableRequest setVariables(final DirectBuffer documentOrEmpty) {
    requestDto.setVariables(documentOrEmpty);
    return this;
  }
}
