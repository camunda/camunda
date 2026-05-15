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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Fluent test client for agent instance commands.
 *
 * <p>The client mimics the wire contract clients will see at the gateway: builder methods that
 * change a single concept (status, metrics, tools) automatically populate {@code changedAttributes}
 * so callers don't have to remember the bookkeeping. Negative tests that need to drive the engine
 * with a deliberately broken {@code changedAttributes} list use {@link #withChangedAttributes} as
 * an escape hatch.
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

  private static final Function<Long, Record<AgentInstanceRecordValue>> UPDATED_EXPECTATION =
      (position) ->
          RecordingExporter.agentInstanceRecords()
              .withIntent(AgentInstanceIntent.UPDATED)
              .withSourceRecordPosition(position)
              .getFirst();

  private static final Function<Long, Record<AgentInstanceRecordValue>>
      UPDATE_REJECTION_EXPECTATION =
          (position) ->
              RecordingExporter.agentInstanceRecords()
                  .onlyCommandRejections()
                  .withIntent(AgentInstanceIntent.UPDATE)
                  .withSourceRecordPosition(position)
                  .getFirst();

  private final CommandWriter writer;
  private final AgentInstanceRecord record = new AgentInstanceRecord();
  private final LinkedHashSet<String> autoChangedAttributes = new LinkedHashSet<>();
  private List<String> overrideChangedAttributes;
  private List<String> authorizedTenantIds = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private boolean expectRejection = false;

  public AgentInstanceClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AgentInstanceClient withElementInstanceKey(final long elementInstanceKey) {
    record.setElementInstanceKey(elementInstanceKey);
    return this;
  }

  public AgentInstanceClient withAgentInstanceKey(final long agentInstanceKey) {
    record.setAgentInstanceKey(agentInstanceKey);
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

  public AgentInstanceClient withStatus(final AgentInstanceStatus status) {
    record.setStatus(status);
    autoChangedAttributes.add("status");
    return this;
  }

  public AgentInstanceClient withMetricsDelta(
      final long inputTokens, final long outputTokens, final int modelCalls, final int toolCalls) {
    record
        .getMetrics()
        .setInputTokens(inputTokens)
        .setOutputTokens(outputTokens)
        .setModelCalls(modelCalls)
        .setToolCalls(toolCalls);
    autoChangedAttributes.add("metrics");
    return this;
  }

  public AgentInstanceClient withTools(final List<AgentInstanceTool> tools) {
    record.setTools(tools);
    autoChangedAttributes.add("tools");
    return this;
  }

  /**
   * Overrides the {@code changedAttributes} list that would otherwise be auto-populated from the
   * builder calls. Used by negative tests that want to drive the engine with an explicitly broken
   * list (e.g. empty, unknown attribute).
   */
  public AgentInstanceClient withChangedAttributes(final List<String> changedAttributes) {
    overrideChangedAttributes = changedAttributes;
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

  public Record<AgentInstanceRecordValue> create(final String username) {
    final long position =
        writer.writeCommand(
            AgentInstanceIntent.CREATE,
            username,
            record,
            authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? CREATE_REJECTION_EXPECTATION : CREATED_EXPECTATION).apply(position);
  }

  public Record<AgentInstanceRecordValue> update() {
    applyChangedAttributes();
    final long position =
        writer.writeCommand(
            record.getAgentInstanceKey(),
            AgentInstanceIntent.UPDATE,
            record,
            authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? UPDATE_REJECTION_EXPECTATION : UPDATED_EXPECTATION).apply(position);
  }

  public Record<AgentInstanceRecordValue> update(final String username) {
    applyChangedAttributes();
    final long position =
        writer.writeCommand(
            record.getAgentInstanceKey(),
            AgentInstanceIntent.UPDATE,
            username,
            record,
            authorizedTenantIds.toArray(new String[0]));
    return (expectRejection ? UPDATE_REJECTION_EXPECTATION : UPDATED_EXPECTATION).apply(position);
  }

  private void applyChangedAttributes() {
    if (overrideChangedAttributes != null) {
      record.setChangedAttributes(overrideChangedAttributes);
    } else {
      record.setChangedAttributes(new ArrayList<>(autoChangedAttributes));
    }
  }

  /** Convenience builder for {@link AgentInstanceTool} values used with {@link #withTools}. */
  public static AgentInstanceTool tool(
      final String name, final String description, final String elementId) {
    return new AgentInstanceTool()
        .setName(name)
        .setDescription(description)
        .setElementId(elementId);
  }

  /** Convenience builder for a list of {@link AgentInstanceTool} values. */
  public static List<AgentInstanceTool> tools(final AgentInstanceTool... tools) {
    return List.of(tools);
  }
}
