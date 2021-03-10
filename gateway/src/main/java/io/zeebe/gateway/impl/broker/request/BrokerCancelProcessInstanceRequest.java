/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class BrokerCancelWorkflowInstanceRequest
    extends BrokerExecuteCommand<WorkflowInstanceRecord> {

  private final WorkflowInstanceRecord requestDto = new WorkflowInstanceRecord();

  public BrokerCancelWorkflowInstanceRequest() {
    super(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL);
  }

  public BrokerCancelWorkflowInstanceRequest setWorkflowInstanceKey(
      final long workflowInstanceKey) {
    request.setKey(workflowInstanceKey);
    return this;
  }

  @Override
  public WorkflowInstanceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected WorkflowInstanceRecord toResponseDto(final DirectBuffer buffer) {
    final WorkflowInstanceRecord responseDto = new WorkflowInstanceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
