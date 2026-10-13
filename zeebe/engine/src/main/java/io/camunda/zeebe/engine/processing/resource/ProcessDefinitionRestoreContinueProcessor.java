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
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.collections.MutableInteger;

/**
 * Visits process definitions in bounded chunks, looking for stray resting {@code PENDING_DELETION}
 * rows left behind by a write-side bug to restore back to {@code ACTIVE}.
 *
 * <p>The cap below bounds the rows <em>visited</em> per command, not the rows matched: each visited
 * {@code PersistedProcess} carries the full BPMN resource blob, so even scanning past many harmless
 * {@code ACTIVE} rows to find a rare stray one must stay bounded to keep a single command's
 * deserialization work and transaction size predictable, regardless of how many definitions exist
 * in state.
 */
@ExcludeAuthorizationCheck
public class ProcessDefinitionRestoreContinueProcessor
    implements TypedRecordProcessor<ProcessDefinitionRestoreRecord> {

  /**
   * Maximum number of process definitions visited by a single command. 50 keeps a worst-case
   * command (50 full resource blobs deserialized) in the same order of magnitude as other engine
   * batch limits (for example the message TTL checker's default batch of 100), while still making
   * steady progress through large installations.
   */
  static final int VISIT_LIMIT = 50;

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

    final var visitedCount = new MutableInteger(0);
    final AtomicReference<ProcessIdentifier> lastVisited = new AtomicReference<>();

    processState.forEachProcess(
        previousProcess,
        persistedProcess -> {
          // copy out of the visitor: the PersistedProcess instance is shared and mutable
          final var tenantId = persistedProcess.getTenantId();
          final var processDefinitionKey = persistedProcess.getKey();
          lastVisited.set(new ProcessIdentifier(tenantId, processDefinitionKey));
          visitedCount.increment();

          if (persistedProcess.getState() == PersistedProcessState.PENDING_DELETION) {
            stateWriter.appendFollowUpEvent(
                processDefinitionKey,
                ProcessDefinitionRestoreIntent.RESTORED,
                new ProcessDefinitionRestoreRecord()
                    .setTenantId(tenantId)
                    .setProcessDefinitionKey(processDefinitionKey));
          }

          return visitedCount.get() < VISIT_LIMIT;
        });

    if (visitedCount.get() < VISIT_LIMIT) {
      // fewer rows than the cap were visited: we reached the end of the process definitions
      stateWriter.appendFollowUpEvent(
          command.getKey(), ProcessDefinitionRestoreIntent.FINISHED, value);
      return;
    }

    final var cursor = lastVisited.get();
    commandWriter.appendFollowUpCommand(
        command.getKey(),
        ProcessDefinitionRestoreIntent.CONTINUE,
        new ProcessDefinitionRestoreRecord()
            .setTenantId(cursor.tenantId())
            .setProcessDefinitionKey(cursor.processDefinitionKey()));
  }
}
