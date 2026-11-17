/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Optional;

/**
 * Identifies which member currently leads each partition, as the cluster topology sees it.
 *
 * <p>The rebalancing coordinator reads leadership from here rather than from the cluster
 * configuration, which says which members replicate a partition and with what priority but not
 * which of them holds leadership right now.
 */
public interface PartitionLeaders {
  /**
   * Obtains a view of the leaders known for a physical tenant's partition group, backed by a single
   * topology snapshot.
   *
   * @param physicalTenantId the partition group to look up leaders for
   * @throws IllegalStateException if the topology for the physical tenant is unavailable
   */
  PartitionGroupLeaders forGroup(String physicalTenantId);

  /** A snapshot of which member leads each partition of a single physical tenant's group. */
  interface PartitionGroupLeaders {
    /**
     * The member leading the given partition, or empty if the topology is known to have no leader
     * for it.
     *
     * @param partitionId the partition id
     */
    Optional<MemberId> currentLeader(int partitionId);
  }
}
