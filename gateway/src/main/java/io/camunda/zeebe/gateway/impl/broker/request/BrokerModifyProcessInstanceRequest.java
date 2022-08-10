/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.TerminateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import org.agrona.DirectBuffer;

public final class BrokerModifyProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceModificationRecord> {

  private final ProcessInstanceModificationRecord requestDto =
      new ProcessInstanceModificationRecord();

  public BrokerModifyProcessInstanceRequest() {
    this(RecordValueWithTenant.DEFAULT_TENANT_ID);
  }

  public BrokerModifyProcessInstanceRequest(final String tenantId) {
    super(ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.MODIFY);
    requestDto.setTenantId(tenantId);
  }

  public BrokerModifyProcessInstanceRequest setProcessInstanceKey(final long processInstanceKey) {
    requestDto.setProcessInstanceKey(processInstanceKey);
    request.setKey(processInstanceKey);
    return this;
  }

  public BrokerModifyProcessInstanceRequest addActivateInstructions(
      final List<ActivateInstruction> activateInstructions) {
    activateInstructions.stream()
        .map(
            activateInstructionReq -> {
              final var activateInstructionResp =
                  new ProcessInstanceModificationActivateInstruction();
              activateInstructionResp
                  .setElementId(activateInstructionReq.getElementId())
                  .setAncestorScopeKey(activateInstructionReq.getAncestorElementInstanceKey());
              activateInstructionReq.getVariableInstructionsList().stream()
                  .map(this::mapVariableInstruction)
                  .forEach(activateInstructionResp::addVariableInstruction);
              return activateInstructionResp;
            })
        .forEach(requestDto::addActivateInstruction);
    return this;
  }

  private ProcessInstanceModificationVariableInstruction mapVariableInstruction(
      final VariableInstruction instruction) {
    return new ProcessInstanceModificationVariableInstruction()
        .setElementId(instruction.getScopeId())
        .setVariables(RequestMapper.ensureJsonSet(instruction.getVariables()));
  }

  public BrokerModifyProcessInstanceRequest addTerminateInstructions(
      final List<TerminateInstruction> terminateInstructions) {
    terminateInstructions.stream()
        .map(
            terminateInstruction ->
                new ProcessInstanceModificationTerminateInstruction()
                    .setElementInstanceKey(terminateInstruction.getElementInstanceKey()))
        .forEach(requestDto::addTerminateInstruction);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceModificationRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceModificationRecord responseDto = new ProcessInstanceModificationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
