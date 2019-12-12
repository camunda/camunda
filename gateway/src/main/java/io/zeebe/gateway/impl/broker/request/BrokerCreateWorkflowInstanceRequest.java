/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import org.agrona.DirectBuffer;

public class BrokerCreateWorkflowInstanceRequest
    extends BrokerExecuteCommand<WorkflowInstanceCreationRecord> {

  private final WorkflowInstanceCreationRecord requestDto = new WorkflowInstanceCreationRecord();

  public BrokerCreateWorkflowInstanceRequest() {
    super(ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE);
  }

  public BrokerCreateWorkflowInstanceRequest setBpmnProcessId(final String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setKey(final long key) {
    requestDto.setWorkflowKey(key);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setVersion(final int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerCreateWorkflowInstanceRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public WorkflowInstanceCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected WorkflowInstanceCreationRecord toResponseDto(final DirectBuffer buffer) {
    final WorkflowInstanceCreationRecord responseDto = new WorkflowInstanceCreationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
