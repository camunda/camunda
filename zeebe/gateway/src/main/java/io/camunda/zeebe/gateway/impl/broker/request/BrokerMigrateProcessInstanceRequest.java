/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public final class BrokerMigrateProcessInstanceRequest
    extends BrokerExecuteCommand<ProcessInstanceMigrationRecord> {

  private final ProcessInstanceMigrationRecord requestDto = new ProcessInstanceMigrationRecord();

  public BrokerMigrateProcessInstanceRequest() {
    super(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationIntent.MIGRATE);
  }

  public BrokerMigrateProcessInstanceRequest setProcessInstanceKey(final long processInstanceKey) {
    requestDto.setProcessInstanceKey(processInstanceKey);
    request.setKey(processInstanceKey);
    return this;
  }

  public BrokerMigrateProcessInstanceRequest setTargetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    requestDto.setTargetProcessDefinitionKey(targetProcessDefinitionKey);
    return this;
  }

  public BrokerMigrateProcessInstanceRequest addMappingInstructions(
      final List<MappingInstruction> mappingInstructions) {
    mappingInstructions.stream()
        .map(
            mappingInstruction ->
                new ProcessInstanceMigrationMappingInstruction()
                    .setSourceElementId(mappingInstruction.getSourceElementId())
                    .setTargetElementId(mappingInstruction.getTargetElementId()))
        .forEach(requestDto::addMappingInstruction);
    return this;
  }

  public BrokerMigrateProcessInstanceRequest setMappingInstructions(
      final List<ProcessInstanceMigrationMappingInstruction> mappingInstructions) {
    mappingInstructions.forEach(requestDto::addMappingInstruction);
    return this;
  }

  @Override
  public ProcessInstanceMigrationRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessInstanceMigrationRecord toResponseDto(final DirectBuffer buffer) {
    final ProcessInstanceMigrationRecord responseDto = new ProcessInstanceMigrationRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
