/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;

public class ProcessInstanceBatchActivatingApplier
    implements TypedEventApplier<ProcessInstanceBatchIntent, ProcessInstanceBatchRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceBatchActivatingApplier(final MutableProcessingState state) {
    elementInstanceState = state.getElementInstanceState();
  }

  @Override
  public void applyState(final long key, final ProcessInstanceBatchRecord value) {
    elementInstanceState.updateInstance(key, mi -> mi.setMultiInstanceBatchActivating(true));
  }
}
