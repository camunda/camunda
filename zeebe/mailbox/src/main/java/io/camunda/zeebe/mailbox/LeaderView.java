/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.util.Optional;

/**
 * The leaders that a node knows about, as far as its cluster membership (gossip) reaches. On a
 * gateway this is the broker topology. On a broker it is the map of partition leaders.
 */
@FunctionalInterface
public interface LeaderView {

  /**
   * @param partitionId the partition
   * @return the leader of the partition, or empty if this node does not know a live leader
   */
  Optional<LeaderInfo> leaderOf(int partitionId);
}
