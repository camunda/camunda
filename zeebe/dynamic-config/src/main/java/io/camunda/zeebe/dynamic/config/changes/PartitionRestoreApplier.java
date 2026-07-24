/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.SortedSet;
import java.util.function.UnaryOperator;

/** Restores a single local partition from the given backups. */
final class PartitionRestoreApplier implements MemberOperationApplier {

  private final MemberId memberId;
  private final int partitionId;
  private final SortedSet<Long> backupIds;
  private final RestoreChangeExecutor restoreChangeExecutor;

  PartitionRestoreApplier(
      final MemberId memberId,
      final int partitionId,
      final SortedSet<Long> backupIds,
      final RestoreChangeExecutor restoreChangeExecutor) {
    this.memberId = memberId;
    this.partitionId = partitionId;
    this.backupIds = backupIds;
    this.restoreChangeExecutor = restoreChangeExecutor;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {
    return RestoreAppliers.requireRecoveringMember(
        currentClusterConfiguration, memberId, partitionId);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    return RestoreAppliers.applyIdentity(restoreChangeExecutor.restore(partitionId, backupIds));
  }
}
