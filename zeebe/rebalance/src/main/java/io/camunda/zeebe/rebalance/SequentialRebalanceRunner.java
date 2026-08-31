/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferProtocol;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RebalanceConfiguration;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a single rebalance request, one partition at a time.
 *
 * <p>This starts by generating the rebalance plan - we identify the desired leader for each
 * partition as well as the current leader. In particular the desired leader must be identified at
 * the start of the run so that we have a fixed target to work towards.
 *
 * <p>A partition already led by its desired leader is completed as {@link
 * PartitionRebalanceOutcome#ALREADY_LEADER}. Every other partition is processed in order, and only
 * one transfer is ever in flight. Each transfer is handed to the partition's current leader, which
 * owns it from there: the coordinator learns of the outcome when that leader reports it back (or by
 * seeing leadership move in the topology), and moves on to the next partition.
 *
 * <pre>
 *   run()
 *     |
 *     | plan(): identify current and desired leaders
 *     v
 *   PENDING --------------------------.
 *     |                               |
 *     |                               v
 *     |                             COMPLETED(ALREADY_LEADER)  (already led by the desired leader)
 *     |                             COMPLETED(PHYSICAL_TENANT_DISABLED)  (its physical tenant
 *     |                                                                   stopped running)
 *     |
 *     | no current leader: wait for one to appear
 *     v
 *   PENDING (waiting) ----------------.
 *     |                               |
 *     |                               v
 *     |                             COMPLETED(NO_LEADER)  (none appeared within leaderWaitTimeout)
 *     |                             COMPLETED(PHYSICAL_TENANT_DISABLED)  (as above, observed
 *     |                                                                   while waiting)
 *     |
 *     | one partition in flight at a time, in order
 *     v
 *   TRANSFERRING ----------------------.
 *     |                                |
 *     |                                v
 *     |                              COMPLETED(<PartitionRebalanceOutcome>)
 *     |                              COMPLETED(NO_RESPONSE)  (no report, no topology move, watchdog
 *     |                                                       expired)
 *     |                              COMPLETED(PHYSICAL_TENANT_DISABLED)  (as above, observed
 *     |                                                                    mid-transfer)
 *     |
 *     | leader reports TRANSFERRED, or the topology confirms the desired leader first
 *     v
 *   COMPLETED(TRANSFERRED)
 * </pre>
 *
 * <p>Runs entirely on the coordinator's actor thread.
 */
public final class SequentialRebalanceRunner implements RebalanceRunner {
  /**
   * How often the topology is checked for a leader appearing, or for the transfer in flight having
   * taken effect.
   */
  @VisibleForTesting static final Duration LEADERSHIP_OBSERVATION_INTERVAL = Duration.ofSeconds(1);

  /** Headroom to apply to the leader-side pause budget for our own per-transfer timeout. */
  @VisibleForTesting static final Duration COORDINATOR_WATCHDOG_SLACK = Duration.ofSeconds(5);

  private static final Logger LOG = LoggerFactory.getLogger(SequentialRebalanceRunner.class);
  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final PartitionLeaders partitionLeaders;
  private final PartitionBalancePlanner planner;
  private final LeadershipTransferProtocol transfers;
  private final ClusterRebalanceMetrics metrics;
  private final Duration leaderWaitTimeout;
  private final RebalanceConfiguration configuration;
  private final Duration heartbeatInterval;

  /** The transfer each partition is currently in, if any. */
  private final Map<PartitionId, ActiveTransfer> activeTransfers = new HashMap<>();

  /** Partitions for which a result handler has already been registered. */
  private final Set<PartitionId> registeredResultHandlers = new HashSet<>();

  public SequentialRebalanceRunner(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final PartitionLeaders partitionLeaders,
      final LeadershipTransferProtocol transfers,
      final ClusterRebalanceMetrics metrics,
      final Duration leaderWaitTimeout,
      final RebalanceConfiguration configuration,
      final Duration heartbeatInterval) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.partitionLeaders = partitionLeaders;
    planner = new PartitionBalancePlanner(partitionLeaders);
    this.transfers = transfers;
    this.metrics = metrics;
    this.leaderWaitTimeout = leaderWaitTimeout;
    this.configuration = configuration;
    this.heartbeatInterval = heartbeatInterval;
  }

  @Override
  public ActorFuture<Void> run(final RebalanceRun rebalance) {
    rebalance.plan(planner.plan(rebalance.configuration()));
    logPlan(rebalance);
    final ActorFuture<Void> completion = executor.createFuture();
    if (rebalance.dryRun()) {
      completion.asNullable().complete(null);
    } else {
      publish(rebalance);
      observePlanned(rebalance);
      transferFrom(rebalance, 0, completion);
    }
    return completion;
  }

  /**
   * Records the partitions resolved in the planning stage (already led by their desired leader).
   */
  private void observePlanned(final RebalanceRun rebalance) {
    for (final var partition : rebalance.partitions()) {
      if (partition.progress() == PartitionRebalanceProgress.COMPLETED) {
        observeTransferOutcome(
            partition, Objects.requireNonNull(partition.outcome()), Duration.ZERO);
      }
    }
  }

  /** Process the first partition that the rebalance still has to transfer. */
  private void transferFrom(
      final RebalanceRun rebalance, final int fromIndex, final ActorFuture<Void> completion) {
    for (int index = fromIndex; index < rebalance.partitionCount(); index++) {
      if (rebalance.isCancelRequested() || rebalance.isAbandoned()) {
        break;
      }
      if (rebalance.partition(index).progress() == PartitionRebalanceProgress.PENDING) {
        rebalance.startPartition();
        initiate(rebalance, index, completion);
        return;
      }
    }
    if (rebalance.isCancelRequested()) {
      cancelPending(rebalance);
    }
    completion.asNullable().complete(null);
  }

  private void initiate(
      final RebalanceRun rebalance, final int index, final ActorFuture<Void> completion) {
    if (resolveIfPhysicalTenantDisabled(rebalance, index, completion)) {
      return;
    }
    final var leader = rebalance.partition(index).currentLeader();
    if (leader == null) {
      waitForLeader(rebalance, index, Duration.ZERO, completion);
      return;
    }
    beginTransfer(rebalance, index, leader, completion);
  }

  private boolean resolveIfPhysicalTenantDisabled(
      final RebalanceRun rebalance, final int index, final ActorFuture<Void> completion) {
    final var partition = rebalance.partition(index);
    if (!rebalance.isPhysicalTenantDisabled(partition.physicalTenantId())) {
      return false;
    }
    LOG.info(
        "Rebalance {} is leaving partition {} alone: its physical tenant has been disabled since "
            + "the rebalance was planned",
        rebalance.id(),
        partition);
    resolveWithOutcome(
        rebalance, index, PartitionRebalanceOutcome.PHYSICAL_TENANT_DISABLED, completion);
    return true;
  }

  /**
   * Waits for a partition with no current leader to get one (up to the rebalance's leader-wait
   * timeout).
   */
  private void waitForLeader(
      final RebalanceRun rebalance,
      final int index,
      final Duration waited,
      final ActorFuture<Void> completion) {
    final var timeout = leaderWaitTimeout(rebalance);
    final var remaining = timeout.minus(waited);
    if (remaining.isZero() || remaining.isNegative()) {
      LOG.warn(
          "Rebalance {} giving up on transferring partition {}: no leader appeared within {}",
          rebalance.id(),
          rebalance.partition(index),
          timeout);
      resolveWithOutcome(rebalance, index, PartitionRebalanceOutcome.NO_LEADER, completion);
      return;
    }
    final var delay =
        remaining.compareTo(LEADERSHIP_OBSERVATION_INTERVAL) < 0
            ? remaining
            : LEADERSHIP_OBSERVATION_INTERVAL;
    executor.schedule(
        delay,
        () -> {
          if (isOver(rebalance, completion)
              || rebalance.partition(index).progress() != PartitionRebalanceProgress.PENDING) {
            return;
          }
          if (rebalance.isCancelRequested()) {
            transferFrom(rebalance, index, completion);
            return;
          }
          if (resolveIfPhysicalTenantDisabled(rebalance, index, completion)) {
            return;
          }
          final var partition = rebalance.partition(index);
          final var observedLeader =
              partitionLeaders
                  .forGroup(partition.physicalTenantId())
                  .currentLeader(partition.partitionId());
          if (observedLeader.isPresent()) {
            rebalance.updatePartition(
                index,
                pending ->
                    new PartitionRebalance(
                        pending.physicalTenantId(),
                        pending.partitionId(),
                        observedLeader.get(),
                        pending.desiredLeader(),
                        PartitionRebalanceProgress.PENDING));
            beginTransfer(rebalance, index, observedLeader.get(), completion);
            return;
          }
          waitForLeader(rebalance, index, waited.plus(delay), completion);
        });
  }

  private void beginTransfer(
      final RebalanceRun rebalance,
      final int index,
      final MemberId leader,
      final ActorFuture<Void> completion) {
    final var partition = rebalance.partition(index);
    final var desiredLeader = partition.desiredLeader();
    rebalance.updatePartition(
        index,
        pending ->
            new PartitionRebalance(
                pending.physicalTenantId(),
                pending.partitionId(),
                leader,
                pending.desiredLeader(),
                PartitionRebalanceProgress.TRANSFERRING));
    publish(rebalance);
    final var partitionId = new PartitionId(partition.physicalTenantId(), partition.partitionId());
    activeTransfers.put(partitionId, new ActiveTransfer(rebalance, index, completion));
    registerResultHandler(partitionId);
    LOG.info(
        "Rebalance {} is requesting {} to transfer leadership of partition {} to desired leader {}",
        rebalance.id(),
        leader,
        partition,
        desiredLeader);
    transfers
        .initiate(leader, partitionId, createTransferInitiateRequest(rebalance, desiredLeader))
        .whenCompleteAsync(
            (response, error) -> onTransferInitiated(rebalance, index, response, error, completion),
            executor);
    watchTransfer(rebalance, index, Duration.ZERO, completion);
  }

  private void registerResultHandler(final PartitionId partitionId) {
    if (!registeredResultHandlers.add(partitionId)) {
      return;
    }
    transfers.onResult(
        partitionId,
        result -> {
          executor.run(() -> onTransferResultReported(partitionId, result));
          return CompletableFuture.completedFuture(
              LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
        });
  }

  /** Watches the topology for the transfer in flight taking effect. */
  private void watchTransfer(
      final RebalanceRun rebalance,
      final int index,
      final Duration waited,
      final ActorFuture<Void> completion) {
    executor.schedule(
        LEADERSHIP_OBSERVATION_INTERVAL,
        () -> {
          if (isOver(rebalance, completion)
              || rebalance.partition(index).progress() != PartitionRebalanceProgress.TRANSFERRING) {
            return;
          }
          if (resolveIfPhysicalTenantDisabled(rebalance, index, completion)) {
            return;
          }
          final var partition = rebalance.partition(index);
          final var observed =
              partitionLeaders
                  .forGroup(partition.physicalTenantId())
                  .currentLeader(partition.partitionId());
          if (observed.map(partition.desiredLeader()::equals).orElse(false)) {
            LOG.info(
                "Rebalance {} saw partition {} led by desired leader {} without notification from "
                    + "previous leader",
                rebalance.id(),
                partition,
                observed.get());
            resolveTransferred(rebalance, index, completion);
            return;
          }
          if (observed.isPresent()
              && !Objects.equals(observed.get(), partition.currentLeader())
              && !observed.get().equals(partition.desiredLeader())) {
            LOG.warn(
                "Rebalance {} saw partition {} led by {} instead of the current or desired leader",
                rebalance.id(),
                partition,
                observed.get());
            resolveLeaderChanged(rebalance, index, observed.get(), completion);
            return;
          }
          final var waitedSoFar = waited.plus(LEADERSHIP_OBSERVATION_INTERVAL);
          final var timeout = transferWatchdogTimeout(rebalance);
          if (waitedSoFar.compareTo(timeout) >= 0) {
            LOG.warn(
                "Rebalance {} giving up on transferring partition {}: current leader neither"
                    + " reported an outcome nor gave up leadership within {}",
                rebalance.id(),
                partition,
                timeout);
            resolveWithOutcome(rebalance, index, PartitionRebalanceOutcome.NO_RESPONSE, completion);
            return;
          }
          watchTransfer(rebalance, index, waitedSoFar, completion);
        });
  }

  private Duration leaderWaitTimeout(final RebalanceRun rebalance) {
    final var override = rebalance.overrides().leaderWaitTimeout();
    return override != null ? override : leaderWaitTimeout;
  }

  private Duration transferWatchdogTimeout(final RebalanceRun rebalance) {
    final var effective = rebalance.overrides().applyTo(configuration);
    return effective.pauseBudget(heartbeatInterval).plus(COORDINATOR_WATCHDOG_SLACK);
  }

  private LeadershipTransferInitiateRequest createTransferInitiateRequest(
      final RebalanceRun rebalance, final MemberId desiredLeader) {
    final var request =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(desiredLeader)
            .withCoordinator(localMemberId)
            .withCoordinatorConfigVersion(rebalance.configuration().globalConfiguration().version())
            .withCorrelationId(rebalance.id());
    final var overrides = rebalance.overrides();
    if (overrides.replicationLagThreshold() != null) {
      request.withReplicationLagThreshold(overrides.replicationLagThreshold());
    }
    if (overrides.replicationTimeout() != null) {
      request.withReplicationTimeout(overrides.replicationTimeout());
    }
    if (overrides.maxTransferAttempts() != null) {
      request.withMaxTransferAttempts(overrides.maxTransferAttempts());
    }
    return request.build();
  }

  private void onTransferInitiated(
      final RebalanceRun rebalance,
      final int index,
      final @Nullable LeadershipTransferInitiateResponse response,
      final @Nullable Throwable error,
      final ActorFuture<Void> completion) {
    if (isOver(rebalance, completion)
        || rebalance.partition(index).progress() != PartitionRebalanceProgress.TRANSFERRING) {
      return;
    }
    final var partition = rebalance.partition(index);
    if (error != null || response == null) {
      LOG.warn(
          "Rebalance {} failed requesting current leader of partition {} to transfer leadership "
              + "(will still watch for outcome)",
          rebalance.id(),
          partition,
          error);
      return;
    }
    if (response.accepted()) {
      return;
    }
    if (response.status() != Status.OK) {
      LOG.warn(
          "Rebalance {} failed requesting current leader of partition {} to transfer leadership: "
              + "{} (will still watch for outcome)",
          rebalance.id(),
          partition,
          response.error());
      return;
    }
    final var rejectionReason = response.rejectionReason();
    LOG.warn(
        "Rebalance {} was declined requesting transfer of partition {}: {}",
        rebalance.id(),
        partition,
        rejectionReason);
    resolveWithOutcome(
        rebalance, index, LeadershipTransferResultMapping.toOutcome(rejectionReason), completion);
  }

  private void onTransferResultReported(
      final PartitionId partitionId, final LeadershipTransferResultRequest result) {
    final var active = activeTransfers.get(partitionId);
    if (active == null) {
      // No transfer is currently active for this partition, e.g. a late result from an earlier
      // rebalance whose transfer of some other partition has since moved on.
      return;
    }
    final var rebalance = active.rebalance();
    final var index = active.index();
    final var completion = active.completion();
    if (isOver(rebalance, completion)
        || result.correlationId() != rebalance.id()
        || rebalance.partition(index).progress() != PartitionRebalanceProgress.TRANSFERRING) {
      // A result left over from an earlier rebalance, or one for a transfer already resolved.
      return;
    }
    final var partition = rebalance.partition(index);
    if (result.result() == LeadershipTransferResult.TRANSFERRED) {
      LOG.info(
          "Rebalance {} moved leadership of partition {} to desired leader {}",
          rebalance.id(),
          partition,
          result.desiredLeader());
      resolveTransferred(rebalance, index, completion);
      return;
    }
    LOG.warn(
        "Rebalance {} transfer of partition {} completed with result {}",
        rebalance.id(),
        partition,
        result.result());
    resolveWithOutcome(
        rebalance, index, LeadershipTransferResultMapping.toOutcome(result.result()), completion);
  }

  /** Completes a partition with a given outcome and moves on to the next. */
  private void resolveWithOutcome(
      final RebalanceRun rebalance,
      final int index,
      final PartitionRebalanceOutcome outcome,
      final ActorFuture<Void> completion) {
    rebalance.updatePartition(index, partition -> partition.completed(outcome));
    observeTransferOutcome(rebalance.partition(index), outcome, rebalance.partitionElapsed());
    publish(rebalance);
    transferFrom(rebalance, index + 1, completion);
  }

  /** Completes a partition with leadership having reached the desired leader. */
  private void resolveTransferred(
      final RebalanceRun rebalance, final int index, final ActorFuture<Void> completion) {
    rebalance.updatePartition(index, PartitionRebalance::transferred);
    observeTransferOutcome(
        rebalance.partition(index),
        PartitionRebalanceOutcome.TRANSFERRED,
        rebalance.partitionElapsed());
    publish(rebalance);
    transferFrom(rebalance, index + 1, completion);
  }

  /** Completes a partition with leadership having moved to a member other than expected. */
  private void resolveLeaderChanged(
      final RebalanceRun rebalance,
      final int index,
      final MemberId newLeader,
      final ActorFuture<Void> completion) {
    rebalance.updatePartition(index, partition -> partition.leaderChanged(newLeader));
    observeTransferOutcome(
        rebalance.partition(index),
        PartitionRebalanceOutcome.LEADER_CHANGED,
        rebalance.partitionElapsed());
    publish(rebalance);
    transferFrom(rebalance, index + 1, completion);
  }

  private void observeTransferOutcome(
      final PartitionRebalance partition,
      final PartitionRebalanceOutcome outcome,
      final Duration took) {
    metrics.observePartitionDuration(
        new PartitionId(partition.physicalTenantId(), partition.partitionId()),
        outcome.name(),
        took);
  }

  private void cancelPending(final RebalanceRun rebalance) {
    var cancelledAny = false;
    for (int index = 0; index < rebalance.partitionCount(); index++) {
      final var partition = rebalance.partition(index);
      if (partition.progress() != PartitionRebalanceProgress.PENDING) {
        continue;
      }
      rebalance.updatePartition(
          index, pending -> pending.completed(PartitionRebalanceOutcome.CANCELLED));
      observeTransferOutcome(partition, PartitionRebalanceOutcome.CANCELLED, Duration.ZERO);
      cancelledAny = true;
    }
    if (cancelledAny) {
      publish(rebalance);
    }
  }

  private void publish(final RebalanceRun rebalance) {
    metrics.setPartitionStates(rebalance.partitions());
  }

  private void logPlan(final RebalanceRun rebalance) {
    final var toTransfer =
        rebalance.partitions().stream()
            .filter(partition -> partition.progress() == PartitionRebalanceProgress.PENDING)
            .toList();
    LOG.info(
        "Rebalance {} sees {} partitions ({} of them already led by desired leader). To be "
            + "transferred: {}",
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

  private static boolean isOver(final RebalanceRun rebalance, final ActorFuture<Void> completion) {
    return completion.isDone() || rebalance.isAbandoned();
  }

  private record ActiveTransfer(RebalanceRun rebalance, int index, ActorFuture<Void> completion) {}
}
