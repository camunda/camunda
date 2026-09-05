/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public final class AgentHistoryClient {

  // Shared by commit() and discard(): both wait for the first follow-up event (COMMITTED or
  // DISCARDED) at the command's source position.
  private static final Function<Long, Record<AgentHistoryRecordValue>> FOLLOW_UP_EVENT_EXPECTATION =
      (position) ->
          RecordingExporter.agentHistoryRecords()
              .onlyEvents()
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final AgentHistoryRecord record = new AgentHistoryRecord();

  public AgentHistoryClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AgentHistoryClient withAgentInstanceKey(final long agentInstanceKey) {
    record.setAgentInstanceKey(agentInstanceKey);
    return this;
  }

  public AgentHistoryClient withJobKey(final long jobKey) {
    record.setJobKey(jobKey);
    return this;
  }

  public AgentHistoryClient withJobLease(final String jobLease) {
    record.setJobLease(jobLease);
    return this;
  }

  public AgentHistoryClient withHistoryItemId(final String historyItemId) {
    record.setHistoryItemId(historyItemId);
    return this;
  }

  public AgentHistoryClient withTenantId(final String tenantId) {
    record.setTenantId(tenantId);
    return this;
  }

  public Record<AgentHistoryRecordValue> commit() {
    final long position = writer.writeCommand(AgentHistoryIntent.COMMIT, record);
    return FOLLOW_UP_EVENT_EXPECTATION.apply(position);
  }

  public Record<AgentHistoryRecordValue> discard() {
    final long position = writer.writeCommand(AgentHistoryIntent.DISCARD, record);
    return FOLLOW_UP_EVENT_EXPECTATION.apply(position);
  }
}
