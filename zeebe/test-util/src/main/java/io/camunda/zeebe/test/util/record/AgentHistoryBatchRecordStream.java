/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AgentHistoryBatchRecordValue;
import java.util.stream.Stream;

public final class AgentHistoryBatchRecordStream
    extends ExporterRecordStream<AgentHistoryBatchRecordValue, AgentHistoryBatchRecordStream> {

  public AgentHistoryBatchRecordStream(
      final Stream<Record<AgentHistoryBatchRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AgentHistoryBatchRecordStream supply(
      final Stream<Record<AgentHistoryBatchRecordValue>> wrappedStream) {
    return new AgentHistoryBatchRecordStream(wrappedStream);
  }

  public AgentHistoryBatchRecordStream withAgentInstanceKey(final long agentInstanceKey) {
    return valueFilter(v -> v.getAgentInstanceKey() == agentInstanceKey);
  }
}
