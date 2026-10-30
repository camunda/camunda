/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class HistoryDeletionClient {
  private static final Function<Long, Record<HistoryDeletionRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.historyDeletionRecords(HistoryDeletionIntent.DELETED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<HistoryDeletionRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.historyDeletionRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final HistoryDeletionRecord record = new HistoryDeletionRecord();
  private Function<Long, Record<HistoryDeletionRecordValue>> expectation = SUCCESS_EXPECTATION;

  public HistoryDeletionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public HistoryDeletionClient processInstance(final long processInstanceKey) {
    record.setResourceType(HistoryDeletionType.PROCESS_INSTANCE);
    record.setResourceKey(processInstanceKey);
    return this;
  }

  public HistoryDeletionClient processDefinition(final long processDefinitionKey) {
    record.setResourceType(HistoryDeletionType.PROCESS_DEFINITION);
    record.setResourceKey(processDefinitionKey);
    return this;
  }

  public HistoryDeletionClient decisionInstance(final long decisionInstanceKey) {
    record.setResourceType(HistoryDeletionType.DECISION_INSTANCE);
    record.setResourceKey(decisionInstanceKey);
    return this;
  }

  public HistoryDeletionClient decisionRequirements(final long decisionRequirementsKey) {
    record.setResourceType(HistoryDeletionType.DECISION_REQUIREMENTS);
    record.setResourceKey(decisionRequirementsKey);
    return this;
  }

  public Record<HistoryDeletionRecordValue> delete() {
    final long position = writer.writeCommand(HistoryDeletionIntent.DELETE, record);
    return expectation.apply(position);
  }

  public HistoryDeletionClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }
}
