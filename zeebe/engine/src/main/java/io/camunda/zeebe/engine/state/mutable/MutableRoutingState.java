/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.RoutingState;
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
   *     tracking the scaling history via {@link RoutingState#bootstrappedAt(int)}
   */
  void setDesiredPartitions(Set<Integer> partitions, long key);

  /**
   * Copies the desired state to the current state. The desired state must be set via {@link
   * #setDesiredPartitions(Set, long)} before calling this method.
   */
  void arriveAtDesiredState();
}
