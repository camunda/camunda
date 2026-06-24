/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import java.util.stream.Stream;

public final class AgentHistoryRecordStream
    extends ExporterRecordStream<AgentHistoryRecordValue, AgentHistoryRecordStream> {

  public AgentHistoryRecordStream(final Stream<Record<AgentHistoryRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AgentHistoryRecordStream supply(
      final Stream<Record<AgentHistoryRecordValue>> wrappedStream) {
    return new AgentHistoryRecordStream(wrappedStream);
  }

  public AgentHistoryRecordStream withAgentInstanceKey(final long agentInstanceKey) {
    return valueFilter(v -> v.getAgentInstanceKey() == agentInstanceKey);
  }

  public AgentHistoryRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public AgentHistoryRecordStream withJobKey(final long jobKey) {
    return valueFilter(v -> v.getJobKey() == jobKey);
  }

  public AgentHistoryRecordStream withRole(final AgentHistoryRole role) {
    return valueFilter(v -> v.getRole() == role);
  }
}
