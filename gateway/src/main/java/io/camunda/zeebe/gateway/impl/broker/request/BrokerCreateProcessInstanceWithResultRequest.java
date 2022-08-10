/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public final class BrokerCreateProcessInstanceWithResultRequest
    extends BrokerExecuteCommand<ProcessInstanceResultRecord> {
  private final ProcessInstanceCreationRecord requestDto = new ProcessInstanceCreationRecord();

  public BrokerCreateProcessInstanceWithResultRequest() {
    this(RecordValueWithTenant.DEFAULT_TENANT_ID);
  }

  public BrokerCreateProcessInstanceWithResultRequest(final String tenantId) {
    super(
        ValueType.PROCESS_INSTANCE_CREATION,
        ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT);
    requestDto.setTenantId(tenantId);
  }

  public BrokerCreateProcessInstanceWithResultRequest setBpmnProcessId(final String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerCreateProcessInstanceWithResultRequest setKey(final long key) {
    requestDto.setProcessDefinitionKey(key);
    return this;
  }

  public BrokerCreateProcessInstanceWithResultRequest setVersion(final int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerCreateProcessInstanceWithResultRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerCreateProcessInstanceWithResultRequest setStartInstructions(
      final List<ProcessInstanceCreationStartInstruction> startInstructionsList) {
    startInstructionsList.stream()
        .map(
            startInstructionReq ->
                new io.camunda.zeebe.protocol.impl.record.value.processinstance
                        .ProcessInstanceCreationStartInstruction()
                    .setElementId(startInstructionReq.getElementId()))
        .forEach(requestDto::addStartInstruction);

    return this;
  }

  public BrokerCreateProcessInstanceWithResultRequest setFetchVariables(
      final List<String> fetchVariables) {
    requestDto.setFetchVariables(fetchVariables);
    return this;
  }

  @Override
  public ProcessInstanceCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceResultRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceResultRecord responseDto = new ProcessInstanceResultRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }

  @Override
  protected boolean isValidResponse() {
    return response.getValueType() == ValueType.PROCESS_INSTANCE_RESULT;
  }
}
