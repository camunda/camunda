/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.BiFunction;

public final class ProcessDefinitionSuspensionClient {

  private final CommandWriter writer;
  private final ProcessDefinitionSuspensionRecord record = new ProcessDefinitionSuspensionRecord();
  private boolean expectRejection = false;

  public ProcessDefinitionSuspensionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ProcessDefinitionSuspensionClient withKey(final long processDefinitionKey) {
    record.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  public ProcessDefinitionSuspensionClient expectRejection() {
    expectRejection = true;
    return this;
  }

  public Record<ProcessDefinitionSuspensionRecordValue> suspend() {
    return send(
        ProcessDefinitionSuspensionIntent.SUSPEND, ProcessDefinitionSuspensionIntent.SUSPENDED);
  }

  public Record<ProcessDefinitionSuspensionRecordValue> resume() {
    return send(
        ProcessDefinitionSuspensionIntent.RESUME, ProcessDefinitionSuspensionIntent.RESUMED);
  }

  private Record<ProcessDefinitionSuspensionRecordValue> send(
      final ProcessDefinitionSuspensionIntent command,
      final ProcessDefinitionSuspensionIntent event) {
    final long position = writer.writeCommand(command, record);
    final BiFunction<
            Long, ProcessDefinitionSuspensionIntent, Record<ProcessDefinitionSuspensionRecordValue>>
        expectation =
            expectRejection
                ? (pos, intent) ->
                    RecordingExporter.processDefinitionSuspensionRecords()
                        .onlyCommandRejections()
                        .withIntent(command)
                        .withSourceRecordPosition(pos)
                        .getFirst()
                : (pos, intent) ->
                    RecordingExporter.processDefinitionSuspensionRecords(intent)
                        .withSourceRecordPosition(pos)
                        .getFirst();
    return expectation.apply(position, event);
  }
}
