/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Which member currently leads each partition, as the cluster topology sees it.
 *
 * <p>The rebalancing coordinator reads leadership from here rather than from the cluster
 * configuration, which says which members replicate a partition and with what priority but not
 * which of them holds leadership right now. The coordinator needs the current leader twice over: it
 * is the member it asks to hand leadership on, and it is how the coordinator sees a transfer take
 * effect even when the leader's own report of the outcome never arrives.
 */
@NullMarked
@FunctionalInterface
public interface PartitionLeaders {

  /**
   * The member leading the given partition, or empty while the topology knows of none - during an
   * election, or before this member has learnt about the partition at all.
   *
   * @param physicalTenantId the partition group the partition belongs to; partition ids are unique
   *     only within a group
   */
  Optional<MemberId> currentLeader(String physicalTenantId, int partitionId);
}
