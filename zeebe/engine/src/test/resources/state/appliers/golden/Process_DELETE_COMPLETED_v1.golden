/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessDeleteDrainState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;

/**
 * Applies {@link ProcessIntent#DELETE_COMPLETED}: clears the reporting partition's outstanding
 * drain report from {@link MutableProcessDeleteDrainState}. The reporting partition is decoded from
 * the event key.
 */
public final class ProcessDeleteCompletedApplier
    implements TypedEventApplier<ProcessIntent, ProcessRecord> {

  private final MutableProcessDeleteDrainState processDeleteDrainState;

  public ProcessDeleteCompletedApplier(final MutableProcessingState state) {
    processDeleteDrainState = state.getProcessDeleteDrainState();
  }

  @Override
  public void applyState(final long key, final ProcessRecord value) {
    final int reportingPartitionId = Protocol.decodePartitionId(key);
    processDeleteDrainState.removeDrainingPartition(
        value.getProcessDefinitionKey(), reportingPartitionId);
  }
}
