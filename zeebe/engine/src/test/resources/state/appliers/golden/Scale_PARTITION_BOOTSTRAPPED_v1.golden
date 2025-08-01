/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartitionBootstrappedApplier implements TypedEventApplier<ScaleIntent, ScaleRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionBootstrappedApplier.class);
  private final MutableRoutingState routingState;

  public PartitionBootstrappedApplier(final MutableProcessingState state) {
    routingState = state.getRoutingState();
  }

  @Override
  public void applyState(final long key, final ScaleRecord value) {
    if (value.getRedistributedPartitions().size() == 1) {
      final var partitionId = value.getRedistributedPartitions().getFirst();
      final var allActivated = routingState.activatePartition(partitionId);
      if (allActivated) {
        LOG.debug("All new partitions have been activated.");
      }
    } else {
      LOG.warn(
          "Received a scale event with more than one partition to redistribute. Ignoring it. Record is {}",
          value);
    }
  }
}
