/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/** Shared validation and completion logic for the new-model restore step appliers. */
final class RestoreAppliers {

  private RestoreAppliers() {}

  /**
   * Rejects the operation unless the member is an active broker of the cluster, part of the group,
   * in recovery for it, and a replica of the partition. Validation only: on success it returns
   * {@link UnaryOperator#identity()}, since no restore step writes the {@link
   * PartitionGroupConfiguration} — see {@link PartitionPreRestoreApplier} and {@link
   * PartitionRestoreApplier} for why the restore graph depends on that.
   */
  static Either<Exception, UnaryOperator<PartitionGroupConfiguration>> requireRecoveringMember(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration,
      final MemberId memberId,
      final int partitionId) {
    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(memberId)
            && currentGlobalConfiguration.getMember(memberId).state() == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for member %s, but the member is not an active member of the cluster"
                  .formatted(memberId)));
    }
    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    if (localBroker == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for member %s, but the member is not part of this partition group"
                  .formatted(memberId)));
    }
    if (localBroker.mode() != Mode.RECOVERING) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for member %s, but the member is not in recovery (mode %s)"
                  .formatted(memberId, localBroker.mode())));
    }
    if (!localBroker.hasPartition(partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for partition %d on member %s, but the member does not replicate that partition"
                  .formatted(partitionId, memberId)));
    }
    return Either.right(UnaryOperator.identity());
  }
}
