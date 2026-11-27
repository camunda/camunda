/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit.contract;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;

/** A running cluster of gossipers that {@link ClusterGossiperContractTest} drives. */
public interface GossipTestCluster extends AutoCloseable {

  /** Returns the gossiper of the current run of the member with the given index. */
  ClusterGossiper gossiper(int index);

  /** Returns the id of the member with the given index. */
  MemberId memberId(int index);

  /** Removes the member from the cluster for good. */
  void stop(int index);

  /** Restarts the member with empty state and returns the gossiper of its new run. */
  ClusterGossiper restart(int index);

  /**
   * Runs the assertion until it passes, letting state move between members in between, and fails if
   * it does not pass in reasonable time.
   */
  void awaitAsserted(Runnable assertion);

  /** Whether the implementation supports GLOBAL keys; GLOBAL tests are skipped otherwise. */
  default boolean supportsGlobalKeys() {
    return true;
  }

  /** Stops every member. */
  @Override
  void close();
}
