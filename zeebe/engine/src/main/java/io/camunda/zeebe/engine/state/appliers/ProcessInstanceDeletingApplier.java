/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableHistoryDeletionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

public class ProcessInstanceDeletingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableHistoryDeletionState historyDeletionState;

  public ProcessInstanceDeletingApplier(final MutableHistoryDeletionState historyDeletionState) {
    this.historyDeletionState = historyDeletionState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    historyDeletionState.insertProcessInstanceToDelete(value.getProcessInstanceKey());
  }
}
