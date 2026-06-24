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
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

public final class AgentHistoryClient {

  private static final Function<Long, Record<AgentHistoryRecordValue>> CREATED_EXPECTATION =
      (position) ->
          RecordingExporter.agentHistoryRecords()
              .withIntent(AgentHistoryIntent.CREATED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<AgentHistoryRecordValue>>
      CREATE_REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.agentHistoryRecords()
                  .onlyCommandRejections()
                  .withIntent(AgentHistoryIntent.CREATE)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final AgentHistoryRecord record = new AgentHistoryRecord();
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private boolean expectRejection = false;

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

  public AgentHistoryClient withElementInstanceKey(final long elementInstanceKey) {
    record.setElementInstanceKey(elementInstanceKey);
    return this;
  }

  public AgentHistoryClient withIteration(final int iteration) {
    record.setIteration(iteration);
    return this;
  }

  public AgentHistoryClient withRole(final AgentHistoryRole role) {
    record.setRole(role);
    return this;
  }

  public AgentHistoryClient withTenantId(final String tenantId) {
    record.setTenantId(tenantId);
    return this;
  }

  public AgentHistoryClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public AgentHistoryClient expectRejection() {
    expectRejection = true;
    return this;
  }

  public Record<AgentHistoryRecordValue> create() {
    final long position =
        writer.writeCommand(
            AgentHistoryIntent.CREATE, record, authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? CREATE_REJECTION_EXPECTATION : CREATED_EXPECTATION).apply(position);
  }

  public Record<AgentHistoryRecordValue> create(final String username) {
    final long position =
        writer.writeCommand(
            AgentHistoryIntent.CREATE,
            username,
            record,
            authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? CREATE_REJECTION_EXPECTATION : CREATED_EXPECTATION).apply(position);
  }
}
