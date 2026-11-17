/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.stream.Stream;

public final class AgentInstanceRecordStream
    extends ExporterRecordStream<AgentInstanceRecordValue, AgentInstanceRecordStream> {

  public AgentInstanceRecordStream(final Stream<Record<AgentInstanceRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AgentInstanceRecordStream supply(
      final Stream<Record<AgentInstanceRecordValue>> wrappedStream) {
    return new AgentInstanceRecordStream(wrappedStream);
  }

  public AgentInstanceRecordStream withAgentInstanceKey(final long agentInstanceKey) {
    return valueFilter(v -> v.getAgentInstanceKey() == agentInstanceKey);
  }

  public AgentInstanceRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public AgentInstanceRecordStream withElementId(final String elementId) {
    return valueFilter(v -> elementId.equals(v.getElementId()));
  }

  public AgentInstanceRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public AgentInstanceRecordStream withStatus(final AgentInstanceStatus status) {
    return valueFilter(v -> v.getStatus() == status);
  }

  public AgentInstanceRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }
}
