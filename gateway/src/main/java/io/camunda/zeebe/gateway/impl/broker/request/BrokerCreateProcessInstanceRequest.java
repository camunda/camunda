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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class BrokerCreateProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceCreationRecord> {

  private final ProcessInstanceCreationRecord requestDto = new ProcessInstanceCreationRecord();

  public BrokerCreateProcessInstanceRequest() {
    super(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE);
  }

  public BrokerCreateProcessInstanceRequest setBpmnProcessId(final String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerCreateProcessInstanceRequest setKey(final long key) {
    requestDto.setProcessDefinitionKey(key);
    return this;
  }

  public BrokerCreateProcessInstanceRequest setVersion(final int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerCreateProcessInstanceRequest setVariables(final DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  public BrokerCreateProcessInstanceRequest setStartInstructions(
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

  public BrokerCreateProcessInstanceRequest setTenantId(final String tenantId) {
    if(!tenantId.isEmpty()) {
      requestDto.setTenantId(tenantId);
    }
    return this;
  }

  @Override
  public ProcessInstanceCreationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceCreationRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceCreationRecord responseDto = new ProcessInstanceCreationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
