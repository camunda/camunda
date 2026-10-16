/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.List;
import java.util.Set;

/**
 * Computes a new partition distribution that satisfies {@code targetMembers}/{@code
 * targetPartitionIds}/{@code replicationFactor}, changing as little as possible relative to the
 * current distribution. Unlike {@link io.camunda.zeebe.dynamic.config.PartitionDistributor}, which
 * always computes a full distribution from scratch, a {@code PartitionReassigner} treats the
 * current assignment as the starting point and only moves what is necessary to reach the target
 * state.
 *
 * <p>{@code targetMembers} and {@code targetPartitionIds} describe the complete desired end state —
 * both existing and new members/ids — not just what's being added. {@code targetPartitionIds} may
 * span several partition groups in a single call, so brokers can be reassigned across multiple
 * physical tenants at once.
 *
 * <p>Whether an existing group or partition id that's absent from {@code targetPartitionIds} is
 * treated as "leave it alone" or "remove it" — or rejected outright — is defined by each
 * implementation, not by this interface: {@link AdditivePartitionReassigner} does not support
 * removal at all and rejects any call that omits an existing partition or group (see {@link
 * PartitionReassignmentSupport#validateExistingPartitionsAreNotRemoved}); a future implementation
 * is expected to support explicit removal, where omitting an id means exactly that. Consult the
 * implementation's javadoc for its specific policy.
 *
 * <p>The order of {@code targetPartitionIds} does not affect the result — implementations process
 * ids in their natural {@link PartitionId} order, not list order, so two calls with the same id set
 * in a different list order produce the same distribution.
 *
 * <p>This single contract covers every combination of scaling changes:
 *
 * <ul>
 *   <li>adding partitions only ({@code targetPartitionIds} grows, members/RF unchanged) — no
 *       existing partition needs to move
 *   <li>adding brokers only ({@code targetMembers} grows, ids/RF unchanged) — some existing
 *       partitions may need to move to use the new capacity, but the number moved should be minimal
 *   <li>adding both partitions and brokers — new partitions are placed to prefer the new brokers,
 *       reducing how many existing partitions need to move
 *   <li>changing the replication factor alone — replicas are added to or removed from existing
 *       partitions without otherwise moving them
 * </ul>
 *
 * <p>The output is the new distribution for all of {@code targetPartitionIds} — both the ids that
 * already existed and the ones newly placed by this call.
 */
public interface PartitionReassigner {

  /**
   * @param currentConfiguration supplies the current distribution of every group that has ids in
   *     {@code targetPartitionIds}, plus every other group's assignments (used only as static
   *     background load, or to enforce an implementation's removal policy)
   * @param targetMembers the complete broker set that should be in play after the change, including
   *     brokers already hosting partitions and any newly added ones; must not be empty
   * @param targetPartitionIds the complete partition-id set that should exist after the change,
   *     including ids that already exist and any newly added ones; may span multiple groups.
   *     Whether omitting an existing id or group is permitted (and what it means) is defined by the
   *     implementation
   * @param replicationFactor the replication factor every partition in {@code targetPartitionIds}
   *     should converge to; must be at least 1
   * @return the new distribution for all of {@code targetPartitionIds}
   * @throws IllegalArgumentException if {@code targetMembers} is empty, {@code replicationFactor}
   *     is not positive, or the implementation's removal policy is violated
   * @throws IllegalStateException an implementation may also throw this for its own
   *     configuration-specific validation failures (e.g. a zone-aware implementation rejecting a
   *     configuration whose zones can't satisfy {@code replicationFactor}) — consult the
   *     implementation's javadoc for which conditions apply
   */
  Set<PartitionMetadata> reassignPartitions(
      CurrentClusterConfiguration currentConfiguration,
      Set<MemberId> targetMembers,
      List<PartitionId> targetPartitionIds,
      int replicationFactor);
}
