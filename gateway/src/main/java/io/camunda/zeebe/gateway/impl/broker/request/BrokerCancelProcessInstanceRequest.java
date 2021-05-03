/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;

public class BrokerCancelProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceRecord> {

  private final ProcessInstanceRecord requestDto = new ProcessInstanceRecord();

  public BrokerCancelProcessInstanceRequest() {
    super(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL);
  }

  public BrokerCancelProcessInstanceRequest setProcessInstanceKey(final long processInstanceKey) {
    request.setKey(processInstanceKey);
    return this;
  }

  @Override
  public ProcessInstanceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceRecord responseDto = new ProcessInstanceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
