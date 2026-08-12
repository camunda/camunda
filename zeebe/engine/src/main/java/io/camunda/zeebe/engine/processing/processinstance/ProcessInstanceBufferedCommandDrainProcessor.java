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
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState.BufferedCommand;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Drains buffered commands one per {@code DRAIN} cycle, then re-checks the buffer (already up to
 * date, since event appliers run synchronously): if more remains it appends the next {@code DRAIN},
 * otherwise it appends {@code COMPLETE_RESUMING} directly to hand the resume finalization off to
 * {@link ProcessInstanceCompleteResumingProcessor} without an extra empty cycle. The bounded-batch
 * iterator shape follows the same pattern as {@link ProcessInstanceBatchActivateProcessor}. Each
 * drained command reaches its own processor unchanged; this processor raises no incidents. {@link
 * SuspensionBehavior#PROCESS} is unconditional: gating a {@code DRAIN} strands the instance in
 * {@code RESUMING} forever.
 *
 * <p>If a cycle fails to write (e.g. the re-emitted command plus its bookkeeping records exceed the
 * batch size limit), draining halts rather than dropping the command: {@link #tryHandleError} makes
 * no further state changes and reports {@link ProcessingError#UNEXPECTED_ERROR}, which the engine
 * turns into a logged error and a rejection of the {@code DRAIN} command — without banning the
 * instance ({@code DRAIN.shouldBanInstance == false}). The buffered command is preserved and the
 * instance stays in {@code RESUMING} until a fresh {@code RESUME} restarts the drain (see {@link
 * ProcessInstanceResumeProcessor}).
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceBufferedCommandDrainProcessor
    implements TypedRecordProcessor<ProcessInstanceBufferedCommandRecord>,
        SuspensionAware<ProcessInstanceBufferedCommandRecord> {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private static final String DRAIN_FAILED_MESSAGE =
      "Expected to resume process instance '{}' by draining a buffered command, but writing it "
          + "back to the log failed. The buffered command is preserved; draining halts here and "
          + "the instance stays in RESUMING until a RESUME command restarts the drain.";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final SuspensionState suspensionState;

  public ProcessInstanceBufferedCommandDrainProcessor(
      final ProcessingState processingState, final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    suspensionState = processingState.getSuspensionState();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey).orElse(null);
    if (buffered == null) {
      appendCompleteResuming(drainValue);
      return;
    }

    appendBufferedCommand(buffered);
    appendDrainedEvent(buffered);

    // DRAINED already applied (event appliers run synchronously), so this reflects the buffer as
    // it stands now, without an extra empty DRAIN cycle to find out
    if (suspensionState.getOldestBufferedCommand(processInstanceKey).isEmpty()) {
      appendCompleteResuming(drainValue);
    } else {
      appendNextDrainCommand(drainValue);
    }
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<ProcessInstanceBufferedCommandRecord> command, final Throwable error) {
    // State-free on purpose: re-reading the buffer here (as a dropped-command path once did) risks
    // throwing again on the same corrupted state and taking the whole partition down with it. Just
    // log and hand off to the engine's default handling, which rejects the DRAIN command without
    // banning the instance (see class javadoc) and halts the chain without touching state.
    LOG.error(DRAIN_FAILED_MESSAGE, command.getValue().getProcessInstanceKey(), error);
    return ProcessingError.UNEXPECTED_ERROR;
  }

  @Override
  public SuspensionBehavior suspensionBehavior(
      final TypedRecord<ProcessInstanceBufferedCommandRecord> record) {
    // DRAIN is what ends the suspension: gating it would strand the instance in RESUMING
    return SuspensionBehavior.PROCESS;
  }

  private void appendBufferedCommand(final BufferedCommand buffered) {
    final var value = buffered.command();
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

  private void appendCompleteResuming(final ProcessInstanceBufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        ProcessInstanceIntent.COMPLETE_RESUMING,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }
}
