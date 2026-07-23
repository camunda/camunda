/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.ExportingStateChangeOperation}, operating on
 * a single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * ExportingStateChangeApplier} in {@code changes/}, which this does not replace or modify: it fans
 * out the change to every partition the member replicates within this group, awaits completion of
 * all of them, and only then updates the member's partition configs to reflect the new state.
 */
public final class ExportingStateChangeApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final ExportingState state;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private Set<Integer> partitions = Set.of();

  public ExportingStateChangeApplier(
      final MemberId memberId,
      final ExportingState state,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.memberId = memberId;
    this.state = state;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    if (!currentGlobalConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to change exporting state, but the member '%s' does not exist in the cluster",
                  memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    if (localBroker == null) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to change exporting state, but the member '%s' is not part of this partition group",
                  memberId)));
    }

    partitions = Set.copyOf(localBroker.partitions().keySet());
    // No state change on init: the config is only updated once the change is applied.
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return partitionChangeExecutor
        .setExportingState(partitions, state)
        .thenApply(ignored -> this::updatePartitionConfigs);
  }

  private PartitionGroupConfiguration updatePartitionConfigs(
      final PartitionGroupConfiguration group) {
    return group.updateMember(
        memberId,
        broker -> {
          var updated = broker;
          for (final int partitionId : partitions) {
            updated =
                updated.updatePartition(
                    partitionId,
                    partition ->
                        partition.updateConfig(
                            config ->
                                config.updateExporting(exporting -> exporting.withState(state))));
          }
          return updated;
        });
  }
}
