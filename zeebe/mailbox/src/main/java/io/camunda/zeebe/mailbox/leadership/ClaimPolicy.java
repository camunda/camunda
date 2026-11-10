/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether this node claims a partition that it does not lead.
 *
 * <ul>
 *   <li>Only the first live node of this region in the priority order of the partition claims, so
 *       the nodes of one region do not compete. Any node may lead any partition, so the priority
 *       order rotates the sorted live nodes by the partition.
 *   <li>A leader of this region is replaced as soon as gossip no longer sees it. The lease does not
 *       play a role, so a failure inside a region is detected as fast as today.
 *   <li>A leader of another region is replaced only when its lease has run out. Gossip cannot see
 *       it, and waiting for the lease keeps a short problem in one region from moving its leaders.
 *   <li>A lease of this node that it does not hold, for example from before a restart, is claimed
 *       again at once.
 * </ul>
 *
 * <p>The claim is a compare-and-set in the store, so if two regions claim at the same time, at most
 * one wins.
 */
final class ClaimPolicy {

  private final String nodeId;
  private final String zone;

  ClaimPolicy(final String nodeId, final String zone) {
    this.nodeId = nodeId;
    this.zone = zone;
  }

  /**
   * @param lease the lease row of the partition, or null if nobody claimed it yet
   * @param leaseHasRunOut whether the observer saw the lease run out
   * @param view what gossip sees of this region
   */
  boolean shouldClaim(
      final int partitionId,
      final @Nullable PartitionLease lease,
      final boolean leaseHasRunOut,
      final ClusterView view) {
    if (lease != null && lease.leaderId().equals(nodeId)) {
      // the other nodes of this region see this node alive, so only this node can take it back
      return true;
    }
    if (!nodeId.equals(firstInPriority(partitionId, view.liveNodesInMyZone()))) {
      return false;
    }
    if (lease == null || lease.released()) {
      return true;
    }
    if (lease.zone().equals(zone)) {
      // a leader that is alive but stopped renewing, for example because it lost the database,
      // has fenced itself, so its partition is claimed once the lease has run out
      return !view.isAlive(lease.leaderId()) || leaseHasRunOut;
    }
    return leaseHasRunOut;
  }

  static @Nullable String firstInPriority(final int partitionId, final List<String> liveNodes) {
    if (liveNodes.isEmpty()) {
      return null;
    }
    final var sorted = liveNodes.stream().sorted().toList();
    return sorted.get(Math.floorMod(partitionId - 1, sorted.size()));
  }
}
