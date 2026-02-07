/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceIntrospectRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntrospectIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerIntrospectProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceIntrospectRecord> {
  private final ProcessInstanceIntrospectRecord requestDto = new ProcessInstanceIntrospectRecord();

  public BrokerIntrospectProcessInstanceRequest() {
    super(ValueType.PROCESS_INSTANCE_INTROSPECT, ProcessInstanceIntrospectIntent.INTROSPECT);
  }

  public BrokerIntrospectProcessInstanceRequest setProcessInstanceKey(
      final long processInstanceKey) {
    requestDto.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceIntrospectRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceIntrospectRecord responseDto = new ProcessInstanceIntrospectRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
