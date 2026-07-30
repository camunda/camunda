/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferProtocol;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
 * rebalance. Each transfer is handed to the partition's current leader, which owns it from there:
 * the coordinator learns of the outcome when that leader reports it back, and moves on to the next
 * partition.
 *
 * <p>A partition the rebalance cannot move is never a reason to abandon the ones after it, so a
 * refusal, a failure, or a leader that cannot be reached is recorded against that partition alone.
 *
 * <p>Runs entirely on the coordinator's actor thread, which is also the thread the {@link
 * RebalanceRun} it is given is confined to.
 */
@NullMarked
public final class SequentialRebalanceRunner implements RebalanceRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SequentialRebalanceRunner.class);

  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final PartitionLeaders partitionLeaders;
  private final LeadershipTransferProtocol transfers;

  public SequentialRebalanceRunner(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final PartitionLeaders partitionLeaders,
      final LeadershipTransferProtocol transfers) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.partitionLeaders = partitionLeaders;
    this.transfers = transfers;
  }

  @Override
  public ActorFuture<Void> run(final RebalanceRun rebalance) {
    rebalance.plan(plan(rebalance.configuration()));
    logPlan(rebalance);
    final ActorFuture<Void> completion = executor.createFuture();
    if (rebalance.dryRun()) {
      // A dry run answers with the plan and stops there: no partition is paused and no leadership
      // moves.
      complete(completion);
    } else {
      transferFrom(rebalance, 0, completion);
    }
    return completion;
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

  /**
   * Takes on the first partition from {@code from} onwards that the rebalance still has to
   * transfer, or finishes the rebalance if there is none left - or if the operator has asked it to
   * stop, which takes effect between partitions so that no transfer is ever abandoned part-way.
   */
  private void transferFrom(
      final RebalanceRun rebalance, final int from, final ActorFuture<Void> completion) {
    for (int index = from; index < rebalance.partitionCount(); index++) {
      if (rebalance.isCancelRequested()) {
        break;
      }
      if (rebalance.partition(index).state() == PartitionRebalanceState.PENDING) {
        initiate(rebalance, index, completion);
        return;
      }
    }
    finish(rebalance, completion);
  }

  private void initiate(
      final RebalanceRun rebalance, final int index, final ActorFuture<Void> completion) {
    final var partition = rebalance.partition(index);
    final var leader = partition.currentLeader();
    final var desiredLeader = partition.desiredLeader();
    if (desiredLeader == null) {
      LOG.warn(
          "Rebalance {} leaves {} alone: it has no desired leader to transfer to",
          rebalance.id(),
          partition);
      resolve(
          rebalance,
          index,
          PartitionRebalanceState.SKIPPED,
          "it has no desired leader to transfer to",
          completion);
      return;
    }
    if (leader == null) {
      // There is somewhere to move leadership to but nobody to ask, so this is a partition the
      // rebalance wanted to move and could not - not one it had no work for.
      LOG.warn("Rebalance {} cannot move {}: it has no leader to ask", rebalance.id(), partition);
      resolve(
          rebalance, index, PartitionRebalanceState.FAILED, "it has no leader to ask", completion);
      return;
    }

    rebalance.updatePartition(
        index, pending -> pending.withState(PartitionRebalanceState.TRANSFERRING));
    // Subscribed before asking, so that a leader that resolves the transfer straight away still
    // finds someone listening for the outcome.
    transfers.onResult(
        partition.physicalTenantId(),
        partition.partitionId(),
        result -> {
          executor.run(() -> onResultReported(rebalance, index, result, completion));
          return CompletableFuture.completedFuture(
              LeadershipTransferResultResponse.builder().withStatus(Status.OK).build());
        });
    LOG.info(
        "Rebalance {} is asking {} to transfer leadership of {} to {}",
        rebalance.id(),
        leader,
        partition,
        desiredLeader);
    transfers
        .initiate(
            leader,
            partition.physicalTenantId(),
            partition.partitionId(),
            initiateRequest(rebalance, desiredLeader))
        .whenComplete(
            (response, error) ->
                executor.run(() -> onInitiated(rebalance, index, response, error, completion)));
  }

  private LeadershipTransferInitiateRequest initiateRequest(
      final RebalanceRun rebalance, final MemberId desiredLeader) {
    final var request =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(desiredLeader)
            .withCoordinator(localMemberId)
            .withCoordinatorConfigVersion(rebalance.configuration().version())
            // One id per rebalance is enough to correlate results: only one transfer of a partition
            // is ever in flight, so a result that quotes this rebalance can only belong to it.
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

  /**
   * Handles the leader's immediate answer: it has either taken the transfer on, in which case the
   * rebalance waits for the outcome it will report, or resolved the request there and then.
   */
  private void onInitiated(
      final RebalanceRun rebalance,
      final int index,
      final @Nullable LeadershipTransferInitiateResponse response,
      final @Nullable Throwable error,
      final ActorFuture<Void> completion) {
    if (completion.isDone()
        || rebalance.partition(index).state() != PartitionRebalanceState.TRANSFERRING) {
      // The transfer resolved before its acknowledgement got back here, or the rebalance is over.
      return;
    }
    final var partition = rebalance.partition(index);
    if (error != null || response == null) {
      LOG.warn(
          "Rebalance {} could not ask the leader of {} to transfer leadership",
          rebalance.id(),
          partition,
          error);
      resolve(
          rebalance,
          index,
          PartitionRebalanceState.FAILED,
          "its leader could not be asked to transfer leadership",
          completion);
      return;
    }
    if (response.accepted()) {
      return;
    }
    final var declinedBecause =
        response.rejectionReason() != null
            ? String.valueOf(response.rejectionReason())
            : String.valueOf(response.error());
    LOG.warn(
        "Rebalance {} had its transfer of {} declined: {}",
        rebalance.id(),
        partition,
        declinedBecause);
    // A declined transfer never froze the partition, so nothing was disrupted - but the rebalance
    // still wanted this partition moved and did not manage it, which is a failure to report rather
    // than a partition to skip over quietly.
    resolve(
        rebalance,
        index,
        PartitionRebalanceState.FAILED,
        "its leader declined the transfer: " + declinedBecause,
        completion);
  }

  /** Handles the outcome the leader reports once the transfer it accepted has resolved. */
  private void onResultReported(
      final RebalanceRun rebalance,
      final int index,
      final LeadershipTransferResultRequest result,
      final ActorFuture<Void> completion) {
    if (completion.isDone()
        || result.correlationId() != rebalance.id()
        || rebalance.partition(index).state() != PartitionRebalanceState.TRANSFERRING) {
      // A result left over from an earlier rebalance, or one for a transfer already resolved.
      return;
    }
    final var partition = rebalance.partition(index);
    if (result.result() == LeadershipTransferResult.TRANSFERRED) {
      LOG.info(
          "Rebalance {} moved leadership of {} to {}",
          rebalance.id(),
          partition,
          result.desiredLeader());
      rebalance.updatePartition(index, PartitionRebalance::transferred);
      transferFrom(rebalance, index + 1, completion);
      return;
    }
    LOG.warn(
        "Rebalance {} left leadership of {} where it was: {}",
        rebalance.id(),
        partition,
        result.result());
    resolve(
        rebalance,
        index,
        PartitionRebalanceState.FAILED,
        "its transfer ran and left leadership where it was: " + result.result(),
        completion);
  }

  private void resolve(
      final RebalanceRun rebalance,
      final int index,
      final PartitionRebalanceState state,
      final @Nullable String reason,
      final ActorFuture<Void> completion) {
    rebalance.updatePartition(index, partition -> partition.withState(state, reason));
    transferFrom(rebalance, index + 1, completion);
  }

  private void finish(final RebalanceRun rebalance, final ActorFuture<Void> completion) {
    final var partitions = rebalance.partitions();
    LOG.info(
        "Rebalance {} transferred {} partitions, skipped {}, failed on {} and left {} untouched",
        rebalance.id(),
        count(partitions, PartitionRebalanceState.TRANSFERRED),
        count(partitions, PartitionRebalanceState.SKIPPED),
        count(partitions, PartitionRebalanceState.FAILED),
        count(partitions, PartitionRebalanceState.PENDING));
    complete(completion);
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

  private static long count(
      final List<PartitionRebalance> partitions, final PartitionRebalanceState state) {
    return partitions.stream().filter(partition -> partition.state() == state).count();
  }

  /** A {@code Void} future carries no value, so completing one means completing it with null. */
  private static void complete(final ActorFuture<Void> completion) {
    completion.asNullable().complete(null);
  }
}
