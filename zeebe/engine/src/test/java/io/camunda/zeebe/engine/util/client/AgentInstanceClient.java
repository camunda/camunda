/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue.AgentInstanceStatus;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.LongFunction;

public final class AgentInstanceClient {

  private static final long DEFAULT_KEY = -1L;

  private static final LongFunction<Record<AgentInstanceRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.agentInstanceRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private static final LongFunction<Record<AgentInstanceRecordValue>> REJECTION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.agentInstanceRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private final AgentInstanceRecord agentInstanceRecord = new AgentInstanceRecord();
  private final CommandWriter writer;
  private LongFunction<Record<AgentInstanceRecordValue>> expectation = SUCCESS_SUPPLIER;

  public AgentInstanceClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public AgentInstanceClient withAgentInstanceKey(final long key) {
    agentInstanceRecord.setAgentInstanceKey(key);
    return this;
  }

  public AgentInstanceClient withElementInstanceKey(final long key) {
    agentInstanceRecord.setElementInstanceKey(key);
    return this;
  }

  public AgentInstanceClient withElementId(final String elementId) {
    agentInstanceRecord.setElementId(elementId);
    return this;
  }

  public AgentInstanceClient withProcessInstanceKey(final long key) {
    agentInstanceRecord.setProcessInstanceKey(key);
    return this;
  }

  public AgentInstanceClient withProcessDefinitionKey(final long key) {
    agentInstanceRecord.setProcessDefinitionKey(key);
    return this;
  }

  public AgentInstanceClient withTenantId(final String tenantId) {
    agentInstanceRecord.setTenantId(tenantId);
    return this;
  }

  public AgentInstanceClient withStatus(final AgentInstanceStatus status) {
    agentInstanceRecord.setStatus(status);
    return this;
  }

  public AgentInstanceClient withModel(final String model) {
    agentInstanceRecord.setModel(model);
    return this;
  }

  public AgentInstanceClient withProvider(final String provider) {
    agentInstanceRecord.setProvider(provider);
    return this;
  }

  public AgentInstanceClient withInputTokens(final long inputTokens) {
    agentInstanceRecord.setInputTokens(inputTokens);
    return this;
  }

  public AgentInstanceClient withOutputTokens(final long outputTokens) {
    agentInstanceRecord.setOutputTokens(outputTokens);
    return this;
  }

  public AgentInstanceClient withModelCalls(final long modelCalls) {
    agentInstanceRecord.setModelCalls(modelCalls);
    return this;
  }

  public AgentInstanceClient withToolCalls(final long toolCalls) {
    agentInstanceRecord.setToolCalls(toolCalls);
    return this;
  }

  public AgentInstanceClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<AgentInstanceRecordValue> create() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            AgentInstanceIntent.CREATE,
            agentInstanceRecord);
    return expectation.apply(position);
  }

  public Record<AgentInstanceRecordValue> update() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            AgentInstanceIntent.UPDATE,
            agentInstanceRecord);
    return expectation.apply(position);
  }

  public Record<AgentInstanceRecordValue> delete() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            AgentInstanceIntent.DELETE,
            agentInstanceRecord);
    return expectation.apply(position);
  }
}
