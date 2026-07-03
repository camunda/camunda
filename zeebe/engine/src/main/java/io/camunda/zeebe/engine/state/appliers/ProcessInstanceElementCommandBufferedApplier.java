/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/**
 * Applies {@link ProcessInstanceIntent#ELEMENT_COMMAND_BUFFERED}: appends the diverted
 * forward-progress element command to the FIFO buffer.
 *
 * <p>The event's own record key (generated via {@code KeyGenerator} by the gate in {@code
 * BpmnStreamProcessor} at the time of buffering) is used as the FIFO sequence — it is monotonic per
 * partition and deterministically recovered on replay, giving the same ordering guarantee as a raw
 * log position without needing to plumb position through the applier interface.
 */
public final class ProcessInstanceElementCommandBufferedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableSuspensionState suspensionState;

  public ProcessInstanceElementCommandBufferedApplier(
      final MutableSuspensionState suspensionState) {
    this.suspensionState = suspensionState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    suspensionState.bufferCommand(value.getProcessInstanceKey(), key, value);
  }
}
