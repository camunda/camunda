/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Drains buffered commands one per {@code DRAIN} cycle, then re-checks the buffer — already
 * current, since event appliers run synchronously — to decide the next step: another {@code DRAIN}
 * if more remains, or {@code RESUME_JOBS} to hand off to {@link
 * ProcessInstanceResumeJobsProcessor}, which un-parks jobs and appends {@code COMPLETE_RESUMING}
 * itself. {@link SuspensionBehavior#PROCESS} is unconditional: gating a {@code DRAIN} would strand
 * the instance in {@code RESUMING} forever.
 *
 * <p>A cycle that fails to write (e.g. batch size exceeded) halts rather than drops the command:
 * the default error handling rejects {@code DRAIN} without banning the instance, and it stays
 * {@code RESUMING} until a fresh {@code RESUME} restarts the drain (see {@link
 * ProcessInstanceResumeProcessor}).
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class BufferedCommandDrainProcessor
    implements TypedRecordProcessor<BufferedCommandRecord>, SuspensionAware<BufferedCommandRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final SuspensionState suspensionState;

  public BufferedCommandDrainProcessor(
      final ProcessingState processingState, final Writers writers) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    suspensionState = processingState.getSuspensionState();
  }

  @Override
  public void processRecord(final TypedRecord<BufferedCommandRecord> command) {
    final var drainValue = command.getValue();
    final long processInstanceKey = drainValue.getProcessInstanceKey();

    final var buffered = suspensionState.getOldestBufferedCommand(processInstanceKey).orElse(null);
    if (buffered == null) {
      appendResumeJobs(drainValue);
      return;
    }

    appendBufferedCommand(buffered);
    appendDrainedEvent(buffered);

    // DRAINED already applied (event appliers run synchronously), so this reflects the buffer as
    // it stands now, without an extra empty DRAIN cycle to find out
    if (suspensionState.getOldestBufferedCommand(processInstanceKey).isEmpty()) {
      appendResumeJobs(drainValue);
    } else {
      appendNextDrainCommand(drainValue);
    }
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<BufferedCommandRecord> record) {
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
        BufferedCommandIntent.DRAINED,
        new BufferedCommandRecord()
            .setProcessInstanceKey(value.getProcessInstanceKey())
            .setProcessDefinitionKey(value.getProcessDefinitionKey())
            .setTenantId(value.getTenantId())
            .setCommandKey(value.getCommandKey())
            .setValueType(value.getValueType())
            .setIntent(value.getIntent()));
  }

  private void appendNextDrainCommand(final BufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        BufferedCommandIntent.DRAIN,
        new BufferedCommandRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }

  private void appendResumeJobs(final BufferedCommandRecord drainValue) {
    commandWriter.appendFollowUpCommand(
        drainValue.getProcessInstanceKey(),
        ProcessInstanceIntent.RESUME_JOBS,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(drainValue.getProcessInstanceKey())
            .setProcessDefinitionKey(drainValue.getProcessDefinitionKey())
            .setTenantId(drainValue.getTenantId()));
  }
}
