/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;

  public ProcessInstanceMigrationMigrateProcessor(final Writers writers) {
    stateWriter = writers.state();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final long processInstanceKey = command.getKey();
    final ProcessInstanceMigrationRecord value = command.getValue();
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }
}
