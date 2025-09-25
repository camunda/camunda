/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.common.state.immutable.RoutingState;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record InMemoryRoutingState(
    Map<Integer, Long> activePartitions,
    Set<Integer> desiredPartitions,
    MessageCorrelation messageCorrelation)
    implements RoutingState {
  @Override
  public Set<Integer> currentPartitions() {
    return activePartitions.keySet();
  }

  @Override
  public Set<Integer> desiredPartitions() {
    return desiredPartitions;
  }

  @Override
  public MessageCorrelation messageCorrelation() {
    return messageCorrelation;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public long scalingStartedAt(final int partitionCount) {
    return Optional.of(activePartitions.get(partitionCount)).orElse(-1L);
  }
}
