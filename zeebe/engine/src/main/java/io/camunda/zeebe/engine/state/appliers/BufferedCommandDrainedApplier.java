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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;

/** Applier for {@link BufferedCommandIntent#DRAINED}. */
final class BufferedCommandDrainedApplier
    implements TypedEventApplier<BufferedCommandIntent, BufferedCommandRecord> {

  private final MutableSuspensionState suspensionState;

  BufferedCommandDrainedApplier(final MutableSuspensionState suspensionState) {
    this.suspensionState = suspensionState;
  }

  @Override
  public void applyState(final long key, final BufferedCommandRecord value) {
    suspensionState.removeBufferedCommand(key);
  }
}
