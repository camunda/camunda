/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessDefinitionRestoreRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;

public final class ProcessDefinitionRestoredApplier
    implements TypedEventApplier<ProcessDefinitionRestoreIntent, ProcessDefinitionRestoreRecord> {

  private final MutableProcessState processState;

  public ProcessDefinitionRestoredApplier(final MutableProcessState processState) {
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessDefinitionRestoreRecord value) {
    // no-op if the definition is no longer PENDING_DELETION, e.g. it was legitimately deleted or
    // already restored since the restore was scheduled; see
    // MutableProcessState#restoreFromPendingDeletion
    processState.restoreFromPendingDeletion(value.getTenantId(), value.getProcessDefinitionKey());
  }
}
