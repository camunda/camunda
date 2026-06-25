/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.placement;

import java.util.List;
import java.util.Map;

/**
 * Decides where a group's partitions are placed. The coordinator is the single placement authority;
 * this is the pluggable policy it uses.
 *
 * <p>Placement is computed over an explicit list of broker node ids — the live set of placement
 * targets — not a cluster size. That is deliberate: supporting a dynamic cluster (brokers joining,
 * advertising capacity) later is then just a different broker list passed in, not a different
 * algorithm. A future rebalancing policy is a second implementation of this interface (or an added
 * method that also takes the current assignment); the change-coordinator that executes a move does
 * not care how the target was computed.
 */
public interface PlacementStrategy {

  /**
   * Assigns {@code partitionCount} partitions, each replicated {@code replicationFactor} times
   * (capped at the number of brokers), across {@code brokers}.
   *
   * @param topic the topic name, used as a deterministic seed so different topics are laid out
   *     differently (the layout stays reproducible and re-derivable after failover — no randomness)
   * @param partitionCount the number of partitions to place
   * @param replicationFactor the desired replica count per partition (capped at {@code brokers})
   * @param brokers the available placement targets (broker node ids), in priority order
   * @return partition id (1-based) → ordered replica node ids; the first is the preferred leader
   */
  Map<Integer, List<Integer>> assign(
      String topic, int partitionCount, int replicationFactor, List<Integer> brokers);
}
