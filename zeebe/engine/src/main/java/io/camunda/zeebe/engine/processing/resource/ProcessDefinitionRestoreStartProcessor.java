/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessDefinitionRestoreRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class ProcessDefinitionRestoreStartProcessor
    implements TypedRecordProcessor<ProcessDefinitionRestoreRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final ProcessState processState;

  public ProcessDefinitionRestoreStartProcessor(
      final Writers writers, final ProcessState processState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessDefinitionRestoreRecord> command) {
    // a START committed before a leader change can still be in the log when the trigger schedules
    // another one on recovery
    if (processState.hasRunProcessDefinitionRestore()) {
      return;
    }

    stateWriter.appendFollowUpEvent(
        command.getKey(), ProcessDefinitionRestoreIntent.STARTED, command.getValue());
    // start the loop from the beginning: no process has been visited yet
    commandWriter.appendFollowUpCommand(
        command.getKey(),
        ProcessDefinitionRestoreIntent.CONTINUE,
        new ProcessDefinitionRestoreRecord());
  }
}
