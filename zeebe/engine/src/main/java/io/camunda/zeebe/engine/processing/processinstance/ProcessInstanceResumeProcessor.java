/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerCheckScheduler;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Resumes a suspended process instance (POC #56552): drains the FIFO buffer of diverted
 * forward-progress element commands, re-appending each as a follow-up command in original order,
 * then marks the instance resumed.
 */
public final class ProcessInstanceResumeProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to resume a process instance with key '%d', but ";
  private static final String NOT_SUSPENDED_MESSAGE = MESSAGE_PREFIX + "it is not suspended";
  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";

  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;
  private final DueDateTimerCheckScheduler timerChecker;

  public ProcessInstanceResumeProcessor(
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final Writers writers,
      final DueDateTimerCheckScheduler timerChecker) {
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.timerChecker = timerChecker;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());
    if (elementInstance == null) {
      reject(command, RejectionType.NOT_FOUND, PROCESS_NOT_FOUND_MESSAGE);
      return;
    }

    if (!suspensionState.isSuspended(command.getKey())) {
      reject(command, RejectionType.INVALID_STATE, NOT_SUSPENDED_MESSAGE);
      return;
    }

    // Collect buffered commands first: the visitor's ProcessInstanceRecord is backed by a shared,
    // mutable buffer that is overwritten on the next state read, so each entry must be copied out
    // before we can safely re-append it as a follow-up command.
    final List<ProcessInstanceRecord> bufferedCommands = new ArrayList<>();
    suspensionState.forEachBufferedCommand(
        command.getKey(),
        record -> {
          final var copy = new ProcessInstanceRecord();
          copy.copyFrom(record);
          bufferedCommands.add(copy);
        });

    for (final ProcessInstanceRecord bufferedCommand : bufferedCommands) {
      final var originalIntent =
          ProcessInstanceIntent.from((short) bufferedCommand.getBufferedElementIntent());
      // Each buffered command targets its own element instance, not the RESUME command's key.
      commandWriter.appendFollowUpCommand(
          bufferedCommand.getBufferedOriginalKey(), originalIntent, bufferedCommand);
    }

    final var value = elementInstance.getValue();
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.RESUMED, value);
    responseWriter.writeEventOnCommand(
        command.getKey(), ProcessInstanceIntent.RESUMED, value, command);

    // nudge the due-date checker so any timer that became due while suspended fires immediately
    // rather than waiting for the next periodic scan (POC #56552)
    final long nudgeAt = command.getTimestamp();
    sideEffectWriter.appendSideEffect(
        () -> {
          timerChecker.scheduleTimer(nudgeAt);
          return true;
        });
  }

  private void reject(
      final TypedRecord<ProcessInstanceRecord> command,
      final RejectionType type,
      final String message) {
    final String formatted = String.format(message, command.getKey());
    rejectionWriter.appendRejection(command, type, formatted);
    responseWriter.writeRejectionOnCommand(command, type, formatted);
  }
}
