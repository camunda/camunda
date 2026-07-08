/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
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

  public AgentHistoryClient withLoopIteration(final int loopIteration) {
    record.setLoopIteration(loopIteration);
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

  public AgentHistoryClient withTextContent(final String text) {
    record.addContent(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText(text));
    return this;
  }

  public AgentHistoryClient withToolCall(
      final String toolCallId, final String toolName, final String elementId) {
    record.addToolCall(
        new AgentHistoryEmbeddedToolCall()
            .setToolCallId(toolCallId)
            .setToolName(toolName)
            .setElementId(elementId)
            .setArguments(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(Map.of()))));
    return this;
  }

  public AgentHistoryClient withMetrics(
      final long inputTokens, final long outputTokens, final long durationMs) {
    record
        .getMetrics()
        .setInputTokens(inputTokens)
        .setOutputTokens(outputTokens)
        .setDurationMs(durationMs);
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

  public Record<AgentHistoryRecordValue> commit() {
    final long position = writer.writeCommand(AgentHistoryIntent.COMMIT, record);
    return FOLLOW_UP_EVENT_EXPECTATION.apply(position);
  }

  public Record<AgentHistoryRecordValue> discard() {
    final long position = writer.writeCommand(AgentHistoryIntent.DISCARD, record);
    return FOLLOW_UP_EVENT_EXPECTATION.apply(position);
  }
}
