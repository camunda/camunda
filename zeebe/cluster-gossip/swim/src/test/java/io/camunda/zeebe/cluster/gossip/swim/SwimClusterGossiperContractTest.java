/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.swim;

import io.camunda.zeebe.cluster.gossip.testkit.contract.ClusterGossiperContractTest;
import io.camunda.zeebe.cluster.gossip.testkit.contract.GossipTestCluster;

/** Runs the contract tests on real clusters with SWIM on localhost. */
final class SwimClusterGossiperContractTest extends ClusterGossiperContractTest {

  @Override
  protected GossipTestCluster startCluster(final int size) {
    return new SwimTestCluster(size);
  }
}
