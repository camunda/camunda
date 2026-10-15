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
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessDefinitionRestoreRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Visits one process definition per command, looking for stray resting {@code PENDING_DELETION}
 * rows left behind by a write-side bug to restore back to {@code ACTIVE}, and chains the next
 * {@code CONTINUE} command.
 *
 * <p>Each visited {@code PersistedProcess} carries the full BPMN resource blob, so a single command
 * deserializes at most one. The engine's command batching groups the chained commands into bounded
 * transactions and lets other commands interleave in between.
 */
@ExcludeAuthorizationCheck
public class ProcessDefinitionRestoreContinueProcessor
    implements TypedRecordProcessor<ProcessDefinitionRestoreRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final ProcessState processState;

  public ProcessDefinitionRestoreContinueProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    processState = processingState.getProcessState();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessDefinitionRestoreRecord> command) {
    final var value = command.getValue();
    final var previousProcess =
        value.getProcessDefinitionKey() < 0
            ? null
            : new ProcessIdentifier(value.getTenantId(), value.getProcessDefinitionKey());

    final var visited = new AtomicReference<ProcessIdentifier>();
    final var stray = new AtomicBoolean();

    processState.forEachProcess(
        previousProcess,
        persistedProcess -> {
          // copy out of the visitor: the PersistedProcess instance is shared and mutable
          visited.set(
              new ProcessIdentifier(persistedProcess.getTenantId(), persistedProcess.getKey()));
          stray.set(persistedProcess.getState() == PersistedProcessState.PENDING_DELETION);
          return false;
        });

    final var cursor = visited.get();
    if (cursor == null) {
      // no definition after the previous one: we reached the end
      stateWriter.appendFollowUpEvent(
          command.getKey(), ProcessDefinitionRestoreIntent.FINISHED, value);
      return;
    }

    if (stray.get()) {
      stateWriter.appendFollowUpEvent(
          cursor.processDefinitionKey(),
          ProcessDefinitionRestoreIntent.RESTORED,
          new ProcessDefinitionRestoreRecord()
              .setTenantId(cursor.tenantId())
              .setProcessDefinitionKey(cursor.processDefinitionKey()));
    }

    commandWriter.appendFollowUpCommand(
        command.getKey(),
        ProcessDefinitionRestoreIntent.CONTINUE,
        new ProcessDefinitionRestoreRecord()
            .setTenantId(cursor.tenantId())
            .setProcessDefinitionKey(cursor.processDefinitionKey()));
  }
}
