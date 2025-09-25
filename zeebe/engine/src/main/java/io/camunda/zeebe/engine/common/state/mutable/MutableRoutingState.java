/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.mutable;

import io.camunda.zeebe.engine.common.state.immutable.RoutingState;
import java.util.Set;

public interface MutableRoutingState extends RoutingState {

  /**
   * Initializes the routing information with the given partition count. If the routing information
   * is already available, this method does nothing.
   */
  void initializeRoutingInfo(int partitionCount);

  /**
   * Creates a new desired state by copying the current state and using the given partitions.
   * Message correlation is not modified and only copied from the current state.
   *
   * @param partitions the set of partition IDs to be used as the desired state. The set must
   *     include current partitions and the new partitions.
   * @param key the event key associated with this scaling operation, which will be stored for
   *     tracking the scaling history via {@link RoutingState#scalingStartedAt(int)}
   */
  void setDesiredPartitions(Set<Integer> partitions, long key);

  /**
   * Move {@param partitionId} from the desired set of partition to the current partitions. The
   * partitions must be activated in order as the partitions must be contiguous.
   *
   * @param partitionId the partition to move
   * @return true if the partition was the last remaining partition in order to arrive at desired
   *     state, false otherwise
   */
  boolean activatePartition(int partitionId);

  void setMessageCorrelation(MessageCorrelation messageCorrelation);
}
