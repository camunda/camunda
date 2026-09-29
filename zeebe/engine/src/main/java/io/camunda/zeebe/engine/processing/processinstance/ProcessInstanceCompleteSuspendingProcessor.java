/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Finalizes the suspend lifecycle once every user task is suspended. Reacts to {@link
 * ProcessInstanceIntent#COMPLETE_SUSPENDING} by writing {@link ProcessInstanceIntent#SUSPENDED}.
 *
 * <p>Writes {@code SUSPENDED} only when the instance exists, the suspension marker is still {@link
 * SuspensionState.State#SUSPENDING}, and the element is not mid-lifecycle-end ({@code
 * ELEMENT_TERMINATING}/{@code ELEMENT_COMPLETING}). Every other case is rejected.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceCompleteSuspendingProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final String INSTANCE_GONE_MESSAGE =
      "Expected to finish suspending process instance '%d', but it no longer exists — likely "
          + "cancelled while suspending.";
  private static final String NOT_SUSPENDING_MESSAGE =
      "Expected to finish suspending process instance '%d', but its suspension marker is no "
          + "longer SUSPENDING.";
  private static final String LIFECYCLE_ENDING_MESSAGE =
      "Expected to finish suspending process instance '%d', but it is %s — suspend is superseded "
          + "by the ending lifecycle.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final SuspensionMetrics suspensionMetrics;

  public ProcessInstanceCompleteSuspendingProcessor(
      final ElementInstanceState elementInstanceState,
      final SuspensionState suspensionState,
      final Writers writers,
      final SuspensionMetrics suspensionMetrics) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.elementInstanceState = elementInstanceState;
    this.suspensionState = suspensionState;
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    if (elementInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return;
    }

    if (suspensionState.getSuspensionState(processInstanceKey)
        != SuspensionState.State.SUSPENDING) {
      reject(command, NOT_SUSPENDING_MESSAGE.formatted(processInstanceKey));
      return;
    }

    final var state = elementInstance.getState();
    if (state == ProcessInstanceIntent.ELEMENT_TERMINATING
        || state == ProcessInstanceIntent.ELEMENT_COMPLETING) {
      reject(command, LIFECYCLE_ENDING_MESSAGE.formatted(processInstanceKey, state));
      return;
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceIntent.SUSPENDED, elementInstance.getValue());
    suspensionMetrics.instanceSuspended();
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.PROCESS;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.PROCESS;
  }

  private void reject(final TypedRecord<ProcessInstanceRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
