/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import org.agrona.DirectBuffer;

public class BrokerSuspendProcessDefinitionRequest
    extends BrokerExecuteCommand<ProcessDefinitionSuspensionRecord> {

  private final ProcessDefinitionSuspensionRecord requestDto =
      new ProcessDefinitionSuspensionRecord();

  public BrokerSuspendProcessDefinitionRequest() {
    super(ValueType.PROCESS_DEFINITION_SUSPENSION, ProcessDefinitionSuspensionIntent.SUSPEND);
  }

  public BrokerSuspendProcessDefinitionRequest setProcessDefinitionKey(
      final long processDefinitionKey) {
    requestDto.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  public BrokerSuspendProcessDefinitionRequest setBpmnProcessId(final String bpmnProcessId) {
    requestDto.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  public BrokerSuspendProcessDefinitionRequest setVersion(final int version) {
    requestDto.setVersion(version);
    return this;
  }

  public BrokerSuspendProcessDefinitionRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  public BrokerSuspendProcessDefinitionRequest setIncludeProcessInstances(
      final boolean includeProcessInstances) {
    requestDto.setIncludeProcessInstances(includeProcessInstances);
    return this;
  }

  @Override
  public ProcessDefinitionSuspensionRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ProcessDefinitionSuspensionRecord toResponseDto(final DirectBuffer buffer) {
    final var responseDto = new ProcessDefinitionSuspensionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
