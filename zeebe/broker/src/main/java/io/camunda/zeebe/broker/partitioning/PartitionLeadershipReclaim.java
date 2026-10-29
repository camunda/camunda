/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.raft.LeadershipTransferResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes back the leadership of partitions that a member handed over before it restarted them. A
 * partition is asked for once its replica on this member has replayed the log it holds, so the new
 * leader does not replay before it processes, and through a coordinated leadership transfer, so it
 * keeps a leader throughout. A partition that cannot be taken back within the bound, or whose
 * replica is paused because it is out of disk space, stays led by the replica that took it over.
 */
@NullMarked
public final class PartitionLeadershipReclaim {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionLeadershipReclaim.class);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

  /** Outcomes after which the leader may admit the transfer if asked again. */
  private static final Set<LeadershipTransferResult> ASK_AGAIN =
      EnumSet.of(
          LeadershipTransferResult.NOT_REPLICATING,
          LeadershipTransferResult.LAG_TOO_HIGH,
          LeadershipTransferResult.TRANSFER_IN_PROGRESS,
          LeadershipTransferResult.LEADER_INITIALIZING,
          LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS,
          LeadershipTransferResult.PAUSE_FAILED,
          LeadershipTransferResult.REPLICATION_TIMED_OUT,
          LeadershipTransferResult.TIMEOUT_NOW_EXHAUSTED,
          LeadershipTransferResult.LEADER_CHANGED);

  private final Duration bound;
  private final long caughtUpLag;
  private final Duration transferBound;
  private final Executor poll;

  /**
   * @param bound how long each partition may take to be installed, catch up and be taken back
   * @param caughtUpLag how many log positions the replica's state may be behind its log to count as
   *     caught up
   * @param transferBound how long one request for the leadership may take
   */
  public PartitionLeadershipReclaim(
      final Duration bound, final long caughtUpLag, final Duration transferBound) {
    this.bound = bound;
    this.caughtUpLag = caughtUpLag;
    this.transferBound = transferBound;
    poll = CompletableFuture.delayedExecutor(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Takes back the leadership of every given partition.
   *
   * @return completes once every partition was taken back or given up, never exceptionally
   */
  public CompletableFuture<List<PartitionReclaim>> reclaim(
      final Collection<? extends ReclaimPartition> partitions) {
    return watch(partitions, true);
  }

  /**
   * Waits until every given partition has caught up with its log, without asking for its
   * leadership.
   *
   * @return completes once every partition caught up or the bound passed, never exceptionally
   */
  public CompletableFuture<List<PartitionReclaim>> awaitCaughtUp(
      final Collection<? extends ReclaimPartition> partitions) {
    return watch(partitions, false);
  }

  private CompletableFuture<List<PartitionReclaim>> watch(
      final Collection<? extends ReclaimPartition> partitions, final boolean takeBack) {
    final var started = System.nanoTime();
    final var reclaims =
        partitions.stream().map(partition -> reclaim(partition, takeBack, started)).toList();
    return CompletableFuture.allOf(reclaims.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> reclaims.stream().map(CompletableFuture::join).toList());
  }

  private CompletableFuture<PartitionReclaim> reclaim(
      final ReclaimPartition partition, final boolean takeBack, final long started) {
    final var progress = new Progress(partition, takeBack, started);
    final var result = new CompletableFuture<PartitionReclaim>();
    CompletableFuture.delayedExecutor(bound.toNanos(), TimeUnit.NANOSECONDS)
        .execute(() -> result.complete(progress.report(Outcome.TIMED_OUT)));
    step(progress, result);
    return result.whenComplete((report, error) -> log(report));
  }

  private void step(final Progress progress, final CompletableFuture<PartitionReclaim> result) {
    if (result.isDone()) {
      return;
    }
    final var partition = progress.partition;
    if (progress.takeBack && partition.leads()) {
      result.complete(progress.report(Outcome.RECLAIMED));
      return;
    }
    partition
        .replayLag()
        .whenComplete(
            (lag, error) -> {
              if (error != null || lag == null) {
                again(progress, result);
                return;
              }
              progress.installed(lag);
              if (lag.pausedForDiskSpace()) {
                result.complete(progress.report(Outcome.PAUSED));
                return;
              }
              if (lag.lag() > caughtUpLag) {
                again(progress, result);
                return;
              }
              progress.caughtUp();
              if (!progress.takeBack) {
                result.complete(progress.report(Outcome.CAUGHT_UP));
                return;
              }
              partition
                  .requestLeadership(transferBound)
                  .whenComplete(
                      (answer, requestError) -> {
                        progress.answered(answer, requestError);
                        if (answer == LeadershipTransferResult.TRANSFERRED
                            || answer == LeadershipTransferResult.ALREADY_LEADER) {
                          result.complete(progress.report(Outcome.RECLAIMED));
                        } else if (requestError != null || ASK_AGAIN.contains(answer)) {
                          again(progress, result);
                        } else {
                          result.complete(progress.report(Outcome.REFUSED));
                        }
                      });
            });
  }

  private void again(final Progress progress, final CompletableFuture<PartitionReclaim> result) {
    CompletableFuture.runAsync(() -> step(progress, result), poll);
  }

  private static void log(final PartitionReclaim report) {
    if (report.outcome() == Outcome.CAUGHT_UP) {
      LOG.info(
          "Partition {} caught up after its restart: installed after {} ms with {} positions of"
              + " log since its snapshot, caught up after {} ms",
          report.partition(),
          report.installedAfter().toMillis(),
          report.logSinceSnapshot(),
          report.caughtUpAfter().toMillis());
    } else if (report.outcome() == Outcome.RECLAIMED) {
      LOG.info(
          "Took back the leadership of partition {} after its restart: installed after {} ms with"
              + " {} positions of log since its snapshot, caught up after {} ms, leading after {} ms",
          report.partition(),
          report.installedAfter().toMillis(),
          report.logSinceSnapshot(),
          report.caughtUpAfter().toMillis(),
          report.took().toMillis());
    } else {
      LOG.info(
          "Partition {} after its restart: {}, installed after {} ms, caught up after {} ms, asked"
              + " for its leadership {}; it stays with its current leader",
          report.partition(),
          report.outcome(),
          report.installedAfter().toMillis(),
          report.caughtUpAfter().toMillis(),
          report.answers());
    }
  }

  /** The replica of a partition on this member, from its restart on. */
  public interface ReclaimPartition {
    int partition();

    boolean leads();

    /** Fails while the replica is not installed. */
    CompletableFuture<ReplayLag> replayLag();

    CompletableFuture<LeadershipTransferResult> requestLeadership(Duration bound);
  }

  /**
   * @param lag how many log positions the replica's state is behind its log
   * @param logSinceSnapshot how many log positions follow the replica's latest snapshot
   * @param pausedForDiskSpace whether the replica's processing is paused because it is out of disk
   *     space; it does not catch up until space is freed or its limit is raised
   */
  public record ReplayLag(long lag, long logSinceSnapshot, boolean pausedForDiskSpace) {

    public ReplayLag(final long lag, final long logSinceSnapshot) {
      this(lag, logSinceSnapshot, false);
    }
  }

  /** How taking back the leadership of one partition went. */
  public enum Outcome {
    /** This member leads the partition again. */
    RECLAIMED,
    /** The replica caught up with its log; its leadership was not asked for. */
    CAUGHT_UP,
    /**
     * The replica is paused because it is out of disk space, so it would not catch up; its
     * leadership was not asked for.
     */
    PAUSED,
    /** The leader refused the transfer for a reason that asking again would not change. */
    REFUSED,
    /** The bound passed before this member led the partition, or before it caught up. */
    TIMED_OUT
  }

  /**
   * @param installedAfter from the start of the reclaim until the replica was first installed, or
   *     the whole time if it never was
   * @param logSinceSnapshot how many log positions followed its snapshot when it was installed,
   *     which it replayed; -1 if it never was
   * @param caughtUpAfter from the start until its state first caught up with its log
   * @param answers what the leader answered to every request for the leadership
   * @param took from the start until the outcome
   */
  public record PartitionReclaim(
      int partition,
      Outcome outcome,
      Duration installedAfter,
      long logSinceSnapshot,
      Duration caughtUpAfter,
      List<String> answers,
      Duration took) {}

  private static final class Progress {
    private final ReclaimPartition partition;
    private final boolean takeBack;
    private final long started;
    private long installedNanos = -1;
    private long logSinceSnapshot = -1;
    private long caughtUpNanos = -1;
    private final List<String> answers = new ArrayList<>();

    private Progress(final ReclaimPartition partition, final boolean takeBack, final long started) {
      this.partition = partition;
      this.takeBack = takeBack;
      this.started = started;
    }

    private synchronized void installed(final ReplayLag lag) {
      if (installedNanos < 0) {
        installedNanos = System.nanoTime();
        logSinceSnapshot = lag.logSinceSnapshot();
      }
    }

    private synchronized void caughtUp() {
      if (caughtUpNanos < 0) {
        caughtUpNanos = System.nanoTime();
      }
    }

    private synchronized void answered(
        final @Nullable LeadershipTransferResult answer, final @Nullable Throwable error) {
      answers.add(error != null ? error.getClass().getSimpleName() : String.valueOf(answer));
    }

    private synchronized PartitionReclaim report(final Outcome outcome) {
      final var now = System.nanoTime();
      return new PartitionReclaim(
          partition.partition(),
          outcome,
          Duration.ofNanos((installedNanos < 0 ? now : installedNanos) - started),
          logSinceSnapshot,
          Duration.ofNanos((caughtUpNanos < 0 ? now : caughtUpNanos) - started),
          List.copyOf(answers),
          Duration.ofNanos(now - started));
    }
  }
}
