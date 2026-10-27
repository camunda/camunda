/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.stream.Stream;

public final class AgentDefinitionRecordStream
    extends ExporterRecordStream<AgentDefinitionRecordValue, AgentDefinitionRecordStream> {

  public AgentDefinitionRecordStream(
      final Stream<Record<AgentDefinitionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AgentDefinitionRecordStream supply(
      final Stream<Record<AgentDefinitionRecordValue>> wrappedStream) {
    return new AgentDefinitionRecordStream(wrappedStream);
  }

  public AgentDefinitionRecordStream withAgentDefinitionKey(final long agentDefinitionKey) {
    return valueFilter(v -> v.getAgentDefinitionKey() == agentDefinitionKey);
  }

  public AgentDefinitionRecordStream withAgentType(final AgentDefinitionType agentType) {
    return valueFilter(v -> v.getAgentType() == agentType);
  }

  public AgentDefinitionRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public AgentDefinitionRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public AgentDefinitionRecordStream withProcessDefinitionKey(final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public AgentDefinitionRecordStream withProcessDefinitionVersion(
      final int processDefinitionVersion) {
    return valueFilter(v -> v.getProcessDefinitionVersion() == processDefinitionVersion);
  }

  public AgentDefinitionRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }
}
