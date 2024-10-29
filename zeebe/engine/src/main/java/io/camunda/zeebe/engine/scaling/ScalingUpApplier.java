/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.util.PartitionUtil;

public class ScalingUpApplier implements TypedEventApplier<ScaleIntent, ScaleRecord> {
  final MutableRoutingState routingState;

  public ScalingUpApplier(final MutableRoutingState routingState) {
    this.routingState = routingState;
  }

  @Override
  public void applyState(final long key, final ScaleRecord value) {
    final var partitionCount = value.getDesiredPartitionCount();
    final var partitions = PartitionUtil.allPartitions(partitionCount);

    routingState.setDesiredPartitions(partitions);
  }
}
