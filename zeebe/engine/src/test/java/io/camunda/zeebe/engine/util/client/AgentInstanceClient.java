/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Function;

/**
 * Fluent test client for agent instance commands.
 *
 * <p>PR1 only carries the CREATE builder; UPDATE (which auto-populates {@code changedAttributes}
 * from the builder calls) lands with PR2.
 */
public final class AgentInstanceClient {

  private static final Function<Long, Record<AgentInstanceRecordValue>> CREATED_EXPECTATION =
      (position) ->
          RecordingExporter.agentInstanceRecords()
              .withIntent(AgentInstanceIntent.CREATED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<AgentInstanceRecordValue>>
      CREATE_REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.agentInstanceRecords()
                  .onlyCommandRejections()
                  .withIntent(AgentInstanceIntent.CREATE)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final AgentInstanceRecord record = new AgentInstanceRecord();
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private boolean expectRejection = false;

  public AgentInstanceClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AgentInstanceClient withElementInstanceKey(final long elementInstanceKey) {
    record.setElementInstanceKey(elementInstanceKey);
    return this;
  }

  public AgentInstanceClient withDefinition(
      final String model, final String provider, final String systemPrompt) {
    record.getDefinition().setModel(model).setProvider(provider).setSystemPrompt(systemPrompt);
    return this;
  }

  public AgentInstanceClient withLimits(
      final long maxTokens, final int maxModelCalls, final int maxToolCalls) {
    record
        .getLimits()
        .setMaxTokens(maxTokens)
        .setMaxModelCalls(maxModelCalls)
        .setMaxToolCalls(maxToolCalls);
    return this;
  }

  /**
   * Sets a status on the command payload. The CREATE processor overwrites this with {@code
   * INITIALIZING}; use this in tests that assert the override.
   */
  public AgentInstanceClient withStatus(final AgentInstanceStatus status) {
    record.setStatus(status);
    return this;
  }

  /**
   * Sets metric values on the command payload. The CREATE processor resets metrics to zero; use
   * this in tests that assert the reset.
   */
  public AgentInstanceClient withMetrics(
      final long inputTokens, final long outputTokens, final int modelCalls, final int toolCalls) {
    record
        .getMetrics()
        .setInputTokens(inputTokens)
        .setOutputTokens(outputTokens)
        .setModelCalls(modelCalls)
        .setToolCalls(toolCalls);
    return this;
  }

  /**
   * Sets a tools list on the command payload. The CREATE processor clears tools; use this in tests
   * that assert the reset.
   */
  public AgentInstanceClient withTools(final List<AgentInstanceTool> tools) {
    record.setTools(tools);
    return this;
  }

  public AgentInstanceClient withTenantId(final String tenantId) {
    record.setTenantId(tenantId);
    return this;
  }

  public AgentInstanceClient withAuthorizedTenantIds(final String... tenantIds) {
    authorizedTenantIds = List.of(tenantIds);
    return this;
  }

  public AgentInstanceClient expectRejection() {
    expectRejection = true;
    return this;
  }

  public Record<AgentInstanceRecordValue> create() {
    final long position =
        writer.writeCommand(
            AgentInstanceIntent.CREATE, record, authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? CREATE_REJECTION_EXPECTATION : CREATED_EXPECTATION).apply(position);
  }
}
