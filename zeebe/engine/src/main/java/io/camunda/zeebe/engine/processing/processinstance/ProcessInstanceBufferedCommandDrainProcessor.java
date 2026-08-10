/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.BufferedCommand;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

/**
 * Replays the commands that were buffered while a process instance was suspended, one per {@code
 * DRAIN} cycle, and writes {@code RESUMED} once the buffer is empty.
 *
 * <p>The drain is an iterator rather than a single atomic pass: each cycle appends one buffered
 * command plus another {@code DRAIN} for itself. A buffer that outgrows a single record batch would
 * otherwise make the instance permanently un-resumable, and chaining leaves the stream processor
 * free to apply its own command batching. {@link ProcessInstanceBatchActivateProcessor} follows the
 * same shape.
 *
 * <p>This processor is deliberately not {@link
 * io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware}: the suspension gate must
 * never buffer or reject a {@code DRAIN}, or the chain would stall with the instance stuck in
 * {@code RESUMING}.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceBufferedCommandDrainProcessor
    implements TypedRecordProcessor<ProcessInstanceBufferedCommandRecord> {

  private static final String DRAIN_FAILED_MESSAGE =
      """
      Expected to resume process instance '%d' by replaying the %s command with key '%d' that was \
      buffered while it was suspended, but it could not be written: %s. The command was dropped so \
      that the remaining buffered commands can still be replayed.""";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final SuspensionState suspensionState;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final KeyGenerator keyGenerator;
  private final IncidentMetrics incidentMetrics;

  public ProcessInstanceBufferedCommandDrainProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final IncidentMetrics incidentMetrics) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    suspensionState = processingState.getSuspensionState();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    this.keyGenerator = keyGenerator;
    this.incidentMetrics = incidentMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey);
    if (buffered == null) {
      writeResumed(processInstanceKey, drainValue);
      return;
    }

    replay(buffered);
    stateWriter.appendFollowUpEvent(
        buffered.key(), ProcessInstanceBufferedCommandIntent.DRAINED, buffered.command());
    appendNextDrainCommand(drainValue);
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceBufferedCommandRecord> command, final Throwable error) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey);
    if (buffered == null) {
      // the failure did not come from replaying a buffered command, so there is nothing to drop and
      // no way to make progress here; fall back to the generic error handling
      return ProcessingError.UNEXPECTED_ERROR;
    }

    raiseIncident(processInstanceKey, buffered, error);
    // Drop the command that could not be replayed, then keep the chain alive. Without the drop the
    // next DRAIN would pick the very same command and fail again, spinning forever; without the
    // follow-up DRAIN the instance would never leave RESUMING and stay un-resumable.
    stateWriter.appendFollowUpEvent(
        buffered.key(), ProcessInstanceBufferedCommandIntent.DRAINED, buffered.command());
    appendNextDrainCommand(drainValue);
    return ProcessingError.EXPECTED_ERROR;
  }

  private void replay(final BufferedCommand buffered) {
    final var value = buffered.command();
    // the replayed command carries no request metadata, so the suspension gate classifies it as
    // internal again and lets it through while the instance is RESUMING
    commandWriter.appendFollowUpCommand(
        value.getCommandKey(), value.getIntent(), value.getCommandValue());
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

  private void writeResumed(
      final long processInstanceKey, final ProcessInstanceBufferedCommandRecord drainValue) {
    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    final ProcessInstanceRecord resumedValue;
    if (elementInstance != null) {
      resumedValue = elementInstance.getValue();
    } else {
      // the instance was cancelled while it was resuming — cancellation is allowed to run on a
      // suspended instance. RESUMED is still written so the suspension marker is removed rather
      // than left behind; the record stays minimal, which also keeps it out of secondary storage.
      resumedValue =
          new ProcessInstanceRecord()
              .setProcessInstanceKey(processInstanceKey)
              .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
              .setTenantId(drainValue.getTenantId());
    }
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceIntent.RESUMED, resumedValue);
  }

  private void raiseIncident(
      final long processInstanceKey, final BufferedCommand buffered, final Throwable error) {
    final var value = buffered.command();
    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(processInstanceKey)
            .build();

    final var incident =
        new IncidentRecord()
            .setErrorType(
                error instanceof ExceededBatchRecordSizeException
                    ? ErrorType.MESSAGE_SIZE_EXCEEDED
                    : ErrorType.UNKNOWN)
            .setErrorMessage(
                DRAIN_FAILED_MESSAGE.formatted(
                    processInstanceKey,
                    value.getValueType(),
                    value.getCommandKey(),
                    error.getMessage()))
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            // the buffered command is not necessarily tied to an element, so the incident is raised
            // on the process instance itself
            .setElementInstanceKey(processInstanceKey)
            .setVariableScopeKey(processInstanceKey)
            .setTenantId(value.getTenantId())
            .setElementInstancePath(treePathProperties.elementInstancePath())
            .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
            .setCallingElementPath(treePathProperties.callingElementPath());

    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    if (elementInstance != null) {
      incident
          .setBpmnProcessId(elementInstance.getValue().getBpmnProcessIdBuffer())
          .setElementId(elementInstance.getValue().getElementIdBuffer());
    }

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incident);
    incidentMetrics.incidentCreated();
  }
}
