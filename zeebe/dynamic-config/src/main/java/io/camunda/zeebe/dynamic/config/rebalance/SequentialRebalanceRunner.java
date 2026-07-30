/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one rebalance, one partition at a time.
 *
 * <p>It starts by planning: every partition of every partition group gets the desired leader the
 * pinned cluster configuration gives it - the replica configured with the highest priority - and
 * whichever member the topology currently shows leading it. Deciding this once, up front, is what
 * makes a rebalance a finite piece of work: it transfers leadership towards a fixed set of targets
 * rather than chasing a cluster that keeps moving.
 *
 * <p>A partition already led by its desired leader is left alone. Every other partition is worked
 * through in order, and only one transfer is ever in flight, because a transfer freezes its
 * partition's writes and a rebalance that froze several at once would be an outage rather than a
 * rebalance.
 *
 * <p>Runs entirely on the coordinator's actor thread, which is also the thread the {@link
 * RebalanceRun} it is given is confined to.
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

  /**
   * Decides what the rebalance will do to each partition. The legacy cluster configuration holds a
   * single partition group, so every partition planned here belongs to the default physical tenant.
   */
  private List<PartitionRebalance> plan(final ClusterConfiguration configuration) {
    return configuration
        .partitionIds()
        .mapToObj(partitionId -> planPartition(configuration, partitionId))
        .toList();
  }

  private PartitionRebalance planPartition(
      final ClusterConfiguration configuration, final int partitionId) {
    final var planned =
        new PartitionRebalance(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            partitionId,
            partitionLeaders
                .currentLeader(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionId)
                .orElse(null),
            configuration.getPrimaryMemberForPartition(partitionId).orElse(null),
            PartitionRebalanceState.PENDING);
    if (planned.desiredLeader() == null) {
      LOG.warn(
          "No member of the cluster configuration is eligible to lead {}, so it cannot be "
              + "rebalanced",
          planned);
      return planned.withState(
          PartitionRebalanceState.SKIPPED,
          "no member of the cluster configuration is eligible to lead it");
    }
    return planned.isBalanced()
        ? planned.withState(
            PartitionRebalanceState.SKIPPED, "leadership is already with the desired leader")
        : planned;
  }

  private void logPlan(final RebalanceRun rebalance) {
    final var toTransfer =
        rebalance.partitions().stream()
            .filter(partition -> partition.state() == PartitionRebalanceState.PENDING)
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
