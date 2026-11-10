/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.PartitionReachability;

/**
 * A leader is reachable if this node knows it. Gossip does not cross the border between regions, so
 * a node never learns about the leaders of another region, and they are not reachable. No
 * comparison of regions is needed.
 */
public final class GossipViewReachability implements PartitionReachability {

  private final LeaderView leaders;

  public GossipViewReachability(final LeaderView leaders) {
    this.leaders = leaders;
  }

  @Override
  public boolean isReachable(final int partitionId) {
    return leaders.leaderOf(partitionId).isPresent();
  }
}
