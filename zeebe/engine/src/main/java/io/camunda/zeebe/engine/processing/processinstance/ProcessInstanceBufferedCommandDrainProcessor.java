/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.BufferedCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Drains buffered commands one per {@code DRAIN} cycle and writes {@code RESUMED} when the buffer
 * is empty. The iterator shape (one command + next {@code DRAIN} per cycle) keeps each batch
 * bounded regardless of buffer size — same pattern as {@link
 * ProcessInstanceBatchActivateProcessor}. Each drained command reaches its own processor unchanged;
 * this processor raises no incidents. {@link SuspensionBehavior#PROCESS} is unconditional: gating a
 * {@code DRAIN} strands the instance in {@code RESUMING} forever.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceBufferedCommandDrainProcessor
    implements TypedRecordProcessor<ProcessInstanceBufferedCommandRecord>,
        SuspensionAware<ProcessInstanceBufferedCommandRecord> {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private static final String DRAIN_FAILED_MESSAGE =
      "Expected to resume process instance '{}' by draining the {} command with key '{}' that was "
          + "buffered while it was suspended, but it could not be written. The command is dropped "
          + "so that the remaining buffered commands can still be drained and the instance can "
          + "leave the RESUMING state.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final SuspensionState suspensionState;
  private final ElementInstanceState elementInstanceState;

  public ProcessInstanceBufferedCommandDrainProcessor(
      final ProcessingState processingState, final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    suspensionState = processingState.getSuspensionState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey);
    if (buffered == null) {
      writeResumed(processInstanceKey);
      return;
    }

    appendBufferedCommand(buffered);
    appendDrainedEvent(buffered);
    appendNextDrainCommand(drainValue);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceBufferedCommandRecord> command, final Throwable error) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey);
    if (buffered == null) {
      // the failure did not come from writing a buffered command, so there is nothing to drop and
      // no way to make progress here; fall back to the generic error handling
      return ProcessingError.UNEXPECTED_ERROR;
    }

    // Log, not incident: command never reached its own processor, which owns the incident state.
    LOG.error(
        DRAIN_FAILED_MESSAGE,
        processInstanceKey,
        buffered.command().getValueType(),
        buffered.command().getCommandKey(),
        error);
    // Drop so the next DRAIN does not spin; keep the chain alive so the instance can leave
    // RESUMING.
    appendDrainedEvent(buffered);
    appendNextDrainCommand(drainValue);
    return ProcessingError.EXPECTED_ERROR;
  }

  @Override
  public SuspensionBehavior suspensionBehavior(
      final TypedRecord<ProcessInstanceBufferedCommandRecord> record) {
    // DRAIN is what ends the suspension: gating it would strand the instance in RESUMING
    return SuspensionBehavior.PROCESS;
  }

  private void appendBufferedCommand(final BufferedCommand buffered) {
    final var value = buffered.command();
    // follow-up commands carry no request metadata, so the gate classifies them as internal
    commandWriter.appendFollowUpCommand(
        value.getCommandKey(), value.getIntent(), value.getCommandValue());
  }

  // DRAINED carries no payload: the applier removes the entry by record key, so repeating it would
  // halve the maximum buffered command size for no gain.
  private void appendDrainedEvent(final BufferedCommand buffered) {
    final var value = buffered.command();
    stateWriter.appendFollowUpEvent(
        buffered.key(),
        ProcessInstanceBufferedCommandIntent.DRAINED,
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setTenantId(value.getTenantId())
            .setCommandKey(value.getCommandKey())
            .setValueType(value.getValueType())
            .setIntent(value.getIntent()));
  }

  private void appendNextDrainCommand(final ProcessInstanceBufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        ProcessInstanceBufferedCommandIntent.DRAIN,
        new ProcessInstanceBufferedCommandRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }

  private void writeResumed(final long processInstanceKey) {
    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    if (elementInstance == null) {
      // Cancelled mid-drain — skip RESUMED to avoid a false audit log entry.
      LOG.debug(
          "Expected to finish resuming process instance '{}', but it no longer exists — likely "
              + "cancelled while resuming. Ending the drain without writing RESUMED.",
          processInstanceKey);
      return;
    }
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceIntent.RESUMED, elementInstance.getValue());
  }
}
