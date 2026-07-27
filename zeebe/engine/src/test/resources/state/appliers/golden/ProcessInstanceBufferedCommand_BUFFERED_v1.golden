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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;

/** Applier for {@link ProcessInstanceBufferedCommandIntent#BUFFERED}. */
final class ProcessInstanceBufferedCommandBufferedApplier
    implements TypedEventApplier<
        ProcessInstanceBufferedCommandIntent, ProcessInstanceBufferedCommandRecord> {

  private final MutableSuspensionState suspensionState;

  ProcessInstanceBufferedCommandBufferedApplier(final MutableSuspensionState suspensionState) {
    this.suspensionState = suspensionState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceBufferedCommandRecord value) {
    suspensionState.bufferCommand(key, value);
  }
}
