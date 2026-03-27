/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import java.util.concurrent.ThreadLocalRandom;

/** Implementations must be thread-safe. */
public interface RequestDispatchStrategy {

  /**
   * Upper bound for the random initial offset. Chosen to be larger than any realistic partition
   * count so that different gateway pods start at well-spread positions in the round-robin cycle,
   * avoiding the thundering-herd problem on startup.
   */
  int MAX_RANDOM_OFFSET = 128;

  /**
   * @return {@link BrokerClusterState#PARTITION_ID_NULL} if no partition can be determined
   */
  int determinePartition(final BrokerTopologyManager topologyManager);

  /**
   * Returns a dispatch strategy which will perform a stateful round robin between the partitions,
   * starting from a random offset to avoid all gateway pods hitting the same partition first.
   */
  static RequestDispatchStrategy roundRobin() {
    return new RoundRobinDispatchStrategy(ThreadLocalRandom.current().nextInt(MAX_RANDOM_OFFSET));
  }
}
