/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Set;

public interface ClusterMembershipChangeExecutor {

  /**
   * The implementation of this method can react to a new node joining the cluster. For example,
   * this can update BrokerInfo so that the gateway can see the new clusterSize.
   *
   * @param memberId id of the broker that is newly added to the cluster
   * @return future when the operation is completed.
   */
  ActorFuture<Void> addBroker(MemberId memberId);

  /**
   * The implementation of this method can react to a node leaving the cluster.
   *
   * @param memberId id of the member that is leaving the cluster
   * @return future when the operation is completed
   */
  ActorFuture<Void> removeBroker(MemberId memberId);

  /**
   * Phase 0 of a force-remove plan (system partition design 5.6.3): removes the brokers from the
   * system partition. Completes once they are not members of the system partition any more.
   *
   * @param removedBrokers the brokers to remove
   * @param acceptPossibleLoss whether a forced change may run although the completeness check fails
   * @return future when the operation is completed
   */
  default ActorFuture<Void> removeSystemPartitionMembers(
      final Set<MemberId> removedBrokers, final boolean acceptPossibleLoss) {
    return CompletableActorFuture.completed(null);
  }

  /**
   * @return true if this executor manages a system partition; force-remove plans then get phase 0
   */
  default boolean managesSystemPartition() {
    return false;
  }
}
