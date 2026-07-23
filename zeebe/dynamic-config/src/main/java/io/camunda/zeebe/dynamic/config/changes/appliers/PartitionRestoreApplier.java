/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.SortedSet;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation}. Mirrors the legacy
 * {@code PartitionRestoreApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class PartitionRestoreApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final int partitionId;
  private final SortedSet<Long> backupIds;
  private final RestoreChangeExecutor restoreChangeExecutor;

  public PartitionRestoreApplier(
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
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return RestoreAppliers.requireRecoveringMember(
        currentGlobalConfiguration, currentPartitionGroupConfiguration, memberId, partitionId);
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return RestoreAppliers.applyIdentity(restoreChangeExecutor.restore(partitionId, backupIds));
  }
}
