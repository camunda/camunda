/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core.coordinator;

import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Maps a consumer group to its coordinator partition. The coordinator Raft group is sharded by
 * {@code groupId} (Kafka {@code __consumer_offsets}-style): all coordination for a group — its
 * membership, rebalances, and committed offsets — is owned by a single partition, so the gateway
 * routes every join/heartbeat/leave/commit for that group to the same coordinator leader.
 *
 * <p>This is Zeebe's <b>message-correlation</b> routing pattern (the one that routes a keyed
 * request to a stable partition), not the round-robin "request handling" pattern used for keyless
 * commands. It is exactly {@code RoutingInfo.StaticRoutingInfo#partitionForCorrelationKey} (engine)
 * with the {@code groupId} as the correlation key: {@link
 * SubscriptionUtil#getSubscriptionPartitionId} (a {@code MessageCorrelation.HashMod} strategy).
 *
 * <p><b>Stability:</b> the count must be the configured, stable shard count — never the live
 * gossiped topology count — so a group never remaps to a different shard (and loses its committed
 * offsets) just because partitions are still being discovered. This mirrors how Zeebe keeps the
 * correlation partition count fixed in {@code RoutingState} during scaling.
 */
public final class CoordinatorRouting {

  /**
   * The coordinator shard that owns the global topic registry. Unlike consumer groups (sharded by
   * group id), topics are a single cluster-wide namespace, so all topic management and the registry
   * broadcast are anchored on one shard. Partition ids are 1-based, so this is the first shard.
   */
  public static final int TOPIC_REGISTRY_SHARD = 1;

  private CoordinatorRouting() {}

  /**
   * Returns the coordinator partition id (1-based, in {@code [1, partitionCount]}) that owns {@code
   * groupId}. For {@code partitionCount == 1} this is always partition 1.
   */
  public static int partitionForGroup(final String groupId, final int partitionCount) {
    final var key = new UnsafeBuffer(groupId.getBytes(StandardCharsets.UTF_8));
    return SubscriptionUtil.getSubscriptionPartitionId(key, partitionCount);
  }
}
