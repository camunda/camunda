/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public final class BrokerCreateWorkflowInstanceWithResultRequest
    extends BrokerExecuteCommand<WorkflowInstanceResultRecord> {
  private final WorkflowInstanceCreationRecord requestDto = new WorkflowInstanceCreationRecord();

  public BrokerCreateWorkflowInstanceWithResultRequest() {
    super(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT);
  }

  public BrokerCreateWorkflowInstanceWithResultRequest setBpmnProcessId(
      final String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerCreateWorkflowInstanceWithResultRequest setKey(final long key) {
    requestDto.setWorkflowKey(key);
    return this;
  }

  public BrokerCreateWorkflowInstanceWithResultRequest setVersion(final int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerCreateWorkflowInstanceWithResultRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerCreateWorkflowInstanceWithResultRequest setFetchVariables(
      final List<String> fetchVariables) {
    requestDto.setFetchVariables(fetchVariables);
    return this;
  }

  @Override
  public WorkflowInstanceCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected WorkflowInstanceResultRecord toResponseDto(final DirectBuffer buffer) {
    final WorkflowInstanceResultRecord responseDto = new WorkflowInstanceResultRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  @Override
  protected boolean isValidResponse() {
    return response.getValueType() == ValueType.WORKFLOW_INSTANCE_RESULT;
  }
}
