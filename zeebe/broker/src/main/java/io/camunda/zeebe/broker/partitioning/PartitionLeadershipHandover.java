/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves the leadership of the partitions a member leads to other replicas before the member stops
 * them, so that the partitions keep a leader instead of waiting for an election.
 *
 * <p>Every partition the member leads is handed over at the same time, each through a coordinated
 * leadership transfer that the member starts itself (see {@link
 * RaftPartition#transferLeadership(MemberId)}). The replicas are tried in the given order of
 * preference; a replica that cannot take over (not caught up, unreachable, did not win) is skipped
 * for the next one. A partition that no replica takes over within the bound is left to the election
 * that follows its stop, as without a handover, and the reason is reported.
 */
@NullMarked
public final class PartitionLeadershipHandover {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionLeadershipHandover.class);

  /** Outcomes after which another replica may still take over. */
  private static final Set<LeadershipTransferResult> TRY_NEXT_REPLICA =
      EnumSet.of(
          LeadershipTransferResult.NOT_MEMBER,
          LeadershipTransferResult.NOT_REPLICATING,
          LeadershipTransferResult.UNREACHABLE,
          LeadershipTransferResult.LAG_TOO_HIGH,
          LeadershipTransferResult.REPLICATION_TIMED_OUT,
          LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED);

  private final MemberId localMember;
  private final Duration bound;

  /**
   * @param localMember the member that is about to stop the partitions
   * @param bound how long all partitions together may take to be handed over
   */
  public PartitionLeadershipHandover(final MemberId localMember, final Duration bound) {
    this.localMember = localMember;
    this.bound = bound;
  }

  /** Hands over the leadership of every given Raft partition that this member leads. */
  public CompletableFuture<Report> handOverRaftPartitions(
      final Collection<RaftPartition> partitions, final Comparator<MemberId> preference) {
    return handOver(partitions.stream().map(RaftHandoverPartition::new).toList(), preference);
  }

  /**
   * Reports which of the given Raft partitions this member leads without handing any over, for a
   * restart with the handover turned off.
   */
  public static Report withoutHandover(final Collection<RaftPartition> partitions) {
    return new Report(
        partitions.stream()
            .map(RaftHandoverPartition::new)
            .map(
                partition ->
                    new PartitionReport(
                        partition.id(),
                        partition.leads() ? Outcome.TURNED_OFF : Outcome.NOT_LEADER,
                        null,
                        List.of(),
                        Duration.ZERO))
            .toList(),
        Duration.ZERO);
  }

  /**
   * Hands over the leadership of every given partition that this member leads.
   *
   * @param preference orders the other replicas, most preferred first
   * @return completes once every partition was handed over or left as it is, never exceptionally
   */
  public CompletableFuture<Report> handOver(
      final Collection<? extends HandoverPartition> partitions,
      final Comparator<MemberId> preference) {
    final var started = System.nanoTime();
    final var handovers =
        partitions.stream().map(partition -> handOver(partition, preference, started)).toList();
    return CompletableFuture.allOf(handovers.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored ->
                new Report(
                    handovers.stream().map(CompletableFuture::join).toList(), since(started)));
  }

  private CompletableFuture<PartitionReport> handOver(
      final HandoverPartition partition,
      final Comparator<MemberId> preference,
      final long started) {
    if (!partition.leads()) {
      return CompletableFuture.completedFuture(
          new PartitionReport(partition.id(), Outcome.NOT_LEADER, null, List.of(), Duration.ZERO));
    }
    final var replicas =
        partition.members().stream()
            .filter(member -> !member.equals(localMember))
            .sorted(preference)
            .toList();
    final var tried = new ArrayList<Attempt>();
    return tryReplicas(partition, replicas, 0, tried)
        .completeOnTimeout(Outcome.TIMED_OUT, bound.toNanos(), TimeUnit.NANOSECONDS)
        .exceptionally(
            error -> {
              LOG.warn("Handing over the leadership of partition {} failed", partition.id(), error);
              return Outcome.FAILED;
            })
        .thenApply(
            outcome -> {
              final List<Attempt> attempts;
              synchronized (tried) {
                attempts = List.copyOf(tried);
              }
              final var newLeader =
                  outcome == Outcome.TRANSFERRED ? attempts.getLast().replica() : null;
              final var report =
                  new PartitionReport(partition.id(), outcome, newLeader, attempts, since(started));
              log(report);
              return report;
            });
  }

  private CompletableFuture<Outcome> tryReplicas(
      final HandoverPartition partition,
      final List<MemberId> replicas,
      final int index,
      final List<Attempt> tried) {
    if (index >= replicas.size()) {
      return CompletableFuture.completedFuture(
          replicas.isEmpty() ? Outcome.NO_OTHER_REPLICA : Outcome.NO_REPLICA_TOOK_OVER);
    }
    final var replica = replicas.get(index);
    return partition
        .transferLeadership(replica)
        .thenCompose(
            result -> {
              synchronized (tried) {
                tried.add(new Attempt(replica, result));
              }
              if (result == LeadershipTransferResult.TRANSFERRED) {
                return CompletableFuture.completedFuture(Outcome.TRANSFERRED);
              }
              if (result == LeadershipTransferResult.LEADER_CHANGED && !partition.leads()) {
                return CompletableFuture.completedFuture(Outcome.LEADERSHIP_LOST);
              }
              if (TRY_NEXT_REPLICA.contains(result)) {
                return tryReplicas(partition, replicas, index + 1, tried);
              }
              return CompletableFuture.completedFuture(Outcome.REFUSED);
            });
  }

  private static void log(final PartitionReport report) {
    if (report.outcome() == Outcome.NOT_LEADER) {
      return;
    }
    if (report.handedOver()) {
      LOG.info(
          "Handed over the leadership of partition {} to {} in {} ms before stopping it",
          report.partition(),
          report.newLeader(),
          report.took().toMillis());
    } else {
      LOG.info(
          "Could not hand over the leadership of partition {} before stopping it ({}; tried {}),"
              + " its replicas elect a new leader",
          report.partition(),
          report.outcome(),
          report.attempts());
    }
  }

  private static Duration since(final long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }

  /** What a member is asked to hand over: one partition replica it may lead. */
  public interface HandoverPartition {
    PartitionId id();

    boolean leads();

    Collection<MemberId> members();

    CompletableFuture<LeadershipTransferResult> transferLeadership(MemberId desiredLeader);
  }

  /** How the leadership of one partition was dealt with. */
  public enum Outcome {
    /** This member did not lead the partition; nothing to hand over. */
    NOT_LEADER,
    /** This member led the partition, and the handover is turned off. */
    TURNED_OFF,
    /** Another replica leads the partition now. */
    TRANSFERRED,
    /** This member stopped leading while the handover ran, without the handover winning. */
    LEADERSHIP_LOST,
    /** The partition has no replica other than this member. */
    NO_OTHER_REPLICA,
    /** Every other replica was tried and none could take over. */
    NO_REPLICA_TOOK_OVER,
    /** The leader refused the transfer for a reason that no other replica would change. */
    REFUSED,
    /** The bound passed before a replica took over. */
    TIMED_OUT,
    /** The handover failed unexpectedly. */
    FAILED
  }

  /** One replica asked to take over, and the outcome. */
  public record Attempt(MemberId replica, LeadershipTransferResult result) {
    @Override
    public String toString() {
      return replica.id() + ": " + result;
    }
  }

  /** The handover of one partition. */
  public record PartitionReport(
      PartitionId partition,
      Outcome outcome,
      @Nullable MemberId newLeader,
      List<Attempt> attempts,
      Duration took) {

    /** Whether the partition no longer depends on this member for its leadership. */
    public boolean handedOver() {
      return outcome == Outcome.TRANSFERRED || outcome == Outcome.LEADERSHIP_LOST;
    }

    /** Whether this member led the partition when the handover started. */
    public boolean led() {
      return outcome != Outcome.NOT_LEADER;
    }
  }

  /** The handover of every partition of one stop. */
  public record Report(List<PartitionReport> partitions, Duration took) {

    public static Report skipped() {
      return new Report(List.of(), Duration.ZERO);
    }

    /** The partitions this member led and could not hand over, with the reason. */
    public String fallbacks() {
      return partitions.stream()
          .filter(partition -> partition.led() && !partition.handedOver())
          .map(partition -> partition.partition() + " " + partition.outcome())
          .collect(Collectors.joining(", "));
    }
  }

  private record RaftHandoverPartition(RaftPartition partition) implements HandoverPartition {
    @Override
    public PartitionId id() {
      return partition.id();
    }

    @Override
    public boolean leads() {
      return partition.getRole() == Role.LEADER;
    }

    @Override
    public Collection<MemberId> members() {
      return partition.members();
    }

    @Override
    public CompletableFuture<LeadershipTransferResult> transferLeadership(
        final MemberId desiredLeader) {
      return partition.transferLeadership(desiredLeader);
    }
  }
}
