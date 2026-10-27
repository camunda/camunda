/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/** Applier for {@link ProcessInstanceIntent#SUSPENDING}. */
final class ProcessInstanceSuspendingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableSuspensionState suspensionState;

  ProcessInstanceSuspendingApplier(final MutableSuspensionState suspensionState) {
    this.suspensionState = suspensionState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    suspensionState.setSuspensionState(key, SuspensionState.State.SUSPENDING);
  }
}
