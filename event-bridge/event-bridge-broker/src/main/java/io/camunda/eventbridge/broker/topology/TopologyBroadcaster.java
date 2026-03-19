/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.topology;

import io.atomix.cluster.Member;
import io.atomix.raft.RaftServer.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reflects the current RAFT partition leadership state of this broker into the local SWIM member's
 * properties map.
 *
 * <p>When this node becomes RAFT leader for a partition, it writes:
 *
 * <pre>
 *   "eb.partition.{id}.leader" = &lt;localMemberId&gt;
 * </pre>
 *
 * When this node loses leadership (transitions to follower, candidate, or inactive), the key is
 * removed.
 *
 * <p>SWIM's gossip protocol automatically detects property changes on the next gossip interval and
 * propagates them to all cluster members. The gateway's {@link TopologyService} consumes these
 * broadcasts and maintains an accurate {@code partitionId → leaderAddress} routing table.
 *
 * <p>If this broker is the coordinator, call {@link #advertiseCoordinator()} once at startup to
 * write {@code "eb.coordinator" = &lt;localMemberId&gt;} into SWIM properties.
 *
 * <p>This class is thread-safe: {@link java.util.Properties} (a {@link java.util.Hashtable}
 * subclass) synchronises all mutations, so concurrent calls from the RAFT role-change thread and
 * the gossip thread are safe.
 */
public final class TopologyBroadcaster {

  private static final Logger LOG = LoggerFactory.getLogger(TopologyBroadcaster.class);

  private final Member localMember;
  private final String localMemberId;

  /**
   * Creates a new broadcaster for the given local cluster member.
   *
   * @param localMember the local SWIM member whose properties will be updated on role changes
   * @param localMemberId the logical member ID of this broker (used as the value in property
   *     entries)
   */
  public TopologyBroadcaster(final Member localMember, final String localMemberId) {
    this.localMember = localMember;
    this.localMemberId = localMemberId;
  }

  /**
   * Called when this broker becomes RAFT leader for the given partition. Writes the partition
   * leader property into SWIM so that gateways can route requests here.
   *
   * @param partitionId the partition for which this node is now leader
   * @param term the RAFT term in which leadership was granted
   */
  public void onBecameLeader(final int partitionId, final long term) {
    final String key = partitionLeaderKey(partitionId);
    localMember.properties().setProperty(key, localMemberId);
    LOG.info(
        "Partition {} became leader (term {}); published '{}={}' to SWIM properties",
        partitionId,
        term,
        key,
        localMemberId);
  }

  /**
   * Called when this broker loses RAFT leadership for the given partition. Removes the partition
   * leader property from SWIM so that gateways stop routing requests here.
   *
   * @param partitionId the partition for which this node is no longer leader
   * @param newRole the role this node transitioned to
   */
  public void onLostLeadership(final int partitionId, final Role newRole) {
    final String key = partitionLeaderKey(partitionId);
    localMember.properties().remove(key);
    LOG.info(
        "Partition {} lost leadership (new role={}); removed '{}' from SWIM properties",
        partitionId,
        newRole,
        key);
  }

  /**
   * Publishes the coordinator identity once at broker startup. Call this method only on the broker
   * whose member ID equals {@code event-bridge.coordinator.broker-id}.
   *
   * <p>The coordinator key is static (not tied to partition leadership) and does not need to be
   * removed on role changes. It is removed automatically by SWIM when this broker leaves the
   * cluster ({@code MEMBER_REMOVED} event).
   */
  public void advertiseCoordinator() {
    localMember.properties().setProperty(TopologyService.COORDINATOR_KEY, localMemberId);
    LOG.info(
        "Coordinator identity published to SWIM properties ('{}={}')",
        TopologyService.COORDINATOR_KEY,
        localMemberId);
  }

  private static String partitionLeaderKey(final int partitionId) {
    return TopologyService.PARTITION_LEADER_KEY_PREFIX
        + partitionId
        + TopologyService.PARTITION_LEADER_KEY_SUFFIX;
  }
}
