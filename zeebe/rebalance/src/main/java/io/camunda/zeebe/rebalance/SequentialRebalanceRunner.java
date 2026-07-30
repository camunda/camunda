/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a single rebalance request, one partition at a time.
 *
 * <p>Runs entirely on the coordinator's actor thread.
 */
@NullMarked
public final class SequentialRebalanceRunner implements RebalanceRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SequentialRebalanceRunner.class);

  private final ConcurrencyControl executor;
  private final PartitionLeaders partitionLeaders;

  public SequentialRebalanceRunner(
      final ConcurrencyControl executor, final PartitionLeaders partitionLeaders) {
    this.executor = executor;
    this.partitionLeaders = partitionLeaders;
  }

  @Override
  public ActorFuture<Void> run(final RebalanceRun rebalance) {
    rebalance.plan(plan(rebalance.configuration()));
    logPlan(rebalance);
    return executor.createCompletedFuture();
  }

  /** Decides what the rebalance will do to each partition. */
  private List<PartitionRebalance> plan(final CurrentClusterConfiguration configuration) {
    return configuration.partitionGroups().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .flatMap(entry -> planGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private Stream<PartitionRebalance> planGroup(
      final String physicalTenantId, final PartitionGroupConfiguration group) {
    final var groupLeaders = partitionLeaders.forGroup(physicalTenantId);
    return group
        .partitionIds()
        .mapToObj(partitionId -> planPartition(physicalTenantId, group, groupLeaders, partitionId));
  }

  private PartitionRebalance planPartition(
      final String physicalTenantId,
      final PartitionGroupConfiguration group,
      final PartitionLeaders.PartitionGroupLeaders groupLeaders,
      final int partitionId) {
    final var desiredLeader =
        group
            .getDesiredLeader(partitionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No member of physical tenant %s's partition group is eligible to lead "
                            + "partition %d, so it cannot be planned"
                                .formatted(physicalTenantId, partitionId)));
    final var currentLeader = groupLeaders.currentLeader(partitionId);
    if (currentLeader.map(desiredLeader::equals).orElse(false)) {
      return PartitionRebalance.alreadyLeader(physicalTenantId, partitionId, desiredLeader);
    }
    return PartitionRebalance.pending(
        physicalTenantId, partitionId, currentLeader.orElse(null), desiredLeader);
  }

  private void logPlan(final RebalanceRun rebalance) {
    final var toTransfer =
        rebalance.partitions().stream()
            .filter(partition -> partition.progress() == PartitionRebalanceProgress.PENDING)
            .toList();
    LOG.info(
        "Rebalance {} covers {} partitions, {} of them already led by the leader it wants. It will "
            + "transfer, in order: {}",
        rebalance.id(),
        rebalance.partitionCount(),
        rebalance.partitionCount() - toTransfer.size(),
        toTransfer.stream()
            .map(
                partition ->
                    "%s from %s to %s"
                        .formatted(partition, partition.currentLeader(), partition.desiredLeader()))
            .toList());
  }
}
