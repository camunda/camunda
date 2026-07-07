/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;

/**
 * Quick-and-dirty gateway request for benchmarking the process instance suspend/resume POC (#56552)
 * against a real cluster (track (c)) — not a reviewed public API surface. One class covering both
 * directions, matching the single-RPC {@code SuspendResumeProcessInstance} shape: the intent is
 * fixed at construction time since {@link BrokerExecuteCommand} requires it up front.
 */
public class BrokerSuspendResumeProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceRecord> {

  private final ProcessInstanceRecord requestDto = new ProcessInstanceRecord();

  public BrokerSuspendResumeProcessInstanceRequest(final boolean resume) {
    super(
        ValueType.PROCESS_INSTANCE,
        resume ? ProcessInstanceIntent.RESUME : ProcessInstanceIntent.SUSPEND);
  }

  public BrokerSuspendResumeProcessInstanceRequest setProcessInstanceKey(
      final long processInstanceKey) {
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
