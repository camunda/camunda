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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerCheckScheduler;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Finalizes the resume lifecycle once the buffered-command drain is complete. Reacts to {@link
 * ProcessInstanceIntent#COMPLETE_RESUMING} by writing {@link ProcessInstanceIntent#RESUMED}.
 *
 * <p>Idempotent by design: writes {@code RESUMED} only when the instance exists, the suspension
 * marker is still {@link SuspensionState.State#RESUMING}, and the element is not mid-lifecycle-end
 * ({@code ELEMENT_TERMINATING}/{@code ELEMENT_COMPLETING}). Every other case is rejected rather
 * than silently skipped, so a concurrent drain chain restarted via {@code RESUME} (see {@link
 * ProcessInstanceResumeProcessor}) cannot write {@code RESUMED} twice.
 *
 * <p>{@link SuspensionBehavior#PROCESS} is unconditional: the marker is still {@code RESUMING} at
 * this point, and gating would strand the instance there forever.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceCompleteResumingProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final String INSTANCE_GONE_MESSAGE =
      "Expected to finish resuming process instance '%d', but it no longer exists — likely "
          + "cancelled while resuming.";
  private static final String ALREADY_FINALIZED_MESSAGE =
      "Expected to finish resuming process instance '%d', but its suspension marker is no "
          + "longer RESUMING — likely already finalized by a concurrent resume.";
  private static final String LIFECYCLE_ENDING_MESSAGE =
      "Expected to finish resuming process instance '%d', but it is %s — resume is superseded "
          + "by the ending lifecycle.";

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final DueDateTimerCheckScheduler timerChecker;

  public ProcessInstanceCompleteResumingProcessor(
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final Writers writers,
      final DueDateTimerCheckScheduler timerChecker) {
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    this.timerChecker = timerChecker;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    if (elementInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return;
    }

    if (suspensionState.getSuspensionState(processInstanceKey) != SuspensionState.State.RESUMING) {
      reject(command, ALREADY_FINALIZED_MESSAGE.formatted(processInstanceKey));
      return;
    }

    final var state = elementInstance.getState();
    if (state == ProcessInstanceIntent.ELEMENT_TERMINATING
        || state == ProcessInstanceIntent.ELEMENT_COMPLETING) {
      reject(command, LIFECYCLE_ENDING_MESSAGE.formatted(processInstanceKey, state));
      return;
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceIntent.RESUMED, elementInstance.getValue());
    // the instance is fully resumed now (the RESUMED applier clears the suspension marker), so
    // any timer that came due and was rejected while suspended can finally fire; nudge the
    // due-date checker rather than waiting for an unrelated future timer to wake it
    sideEffectWriter.appendSideEffect(
        () -> {
          timerChecker.scheduleTimer(-1);
        });
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionBehavior.PROCESS;
  }

  private void reject(final TypedRecord<ProcessInstanceRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
