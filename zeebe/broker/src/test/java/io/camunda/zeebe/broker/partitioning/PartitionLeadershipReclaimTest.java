/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.LeadershipTransferResult;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.Outcome;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.PartitionReclaim;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.ReclaimPartition;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.ReplayLag;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PartitionLeadershipReclaimTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final long CAUGHT_UP = 100;

  private static final Duration BOUND = Duration.ofSeconds(2);

  private final PartitionLeadershipReclaim reclaim =
      new PartitionLeadershipReclaim(BOUND, CAUGHT_UP, Duration.ofSeconds(1));

  @Test
  void shouldAskForTheLeadershipOnlyOnceCaughtUp() {
    // given
    final var partition =
        new FakePartition(
            List.of(lag(5000), lag(800), lag(40)), List.of(LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.RECLAIMED);
    assertThat(report.answers()).containsExactly("TRANSFERRED");
    assertThat(partition.lagReadsBeforeRequest.get()).isEqualTo(3);
    assertThat(report.logSinceSnapshot()).isEqualTo(5000);
  }

  @Test
  void shouldWaitUntilThePartitionIsInstalled() {
    // given
    final var partition =
        new FakePartition(List.of(lag(10)), List.of(LeadershipTransferResult.TRANSFERRED));
    partition.uninstalledReads = 3;

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.RECLAIMED);
  }

  @Test
  void shouldAskAgainWhileTheLeaderFindsItBehind() {
    // given
    final var partition =
        new FakePartition(
            List.of(lag(10)),
            List.of(
                LeadershipTransferResult.LAG_TOO_HIGH,
                LeadershipTransferResult.TRANSFER_IN_PROGRESS,
                LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.RECLAIMED);
    assertThat(report.answers())
        .containsExactly("LAG_TOO_HIGH", "TRANSFER_IN_PROGRESS", "TRANSFERRED");
  }

  @Test
  void shouldStopWhenTheLeaderRefusesForGood() {
    // given
    final var partition =
        new FakePartition(List.of(lag(10)), List.of(LeadershipTransferResult.NOT_MEMBER));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.REFUSED);
  }

  @Test
  void shouldGiveUpWhenThePartitionNeverCatchesUp() {
    // given
    final var partition =
        new FakePartition(List.of(lag(1_000_000)), List.of(LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.TIMED_OUT);
    assertThat(report.answers()).isEmpty();
  }

  @Test
  void shouldKeepWaitingWhileTheReplicaCatchesUpForLongerThanTheBound() {
    // given
    final var lags = new ArrayList<ReplayLag>();
    for (long lag = 300_000; lag > CAUGHT_UP; lag -= 1000) {
      lags.add(lag(lag));
    }
    lags.add(lag(40));
    final var partition = new FakePartition(lags, List.of(LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.RECLAIMED);
    assertThat(report.caughtUpAfter()).isGreaterThan(BOUND);
  }

  @Test
  void shouldLeaveTheLeadershipWhereItIsWhileTheReplicaIsOutOfDiskSpace() {
    // given
    final var partition =
        new FakePartition(
            List.of(new ReplayLag(5000, 5000, true)),
            List.of(LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaimed(partition);

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.PAUSED);
    assertThat(report.answers()).isEmpty();
    assertThat(report.took()).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  void shouldNotAskForTheLeadershipWhenOnlyAwaitingTheCatchUp() {
    // given
    final var partition =
        new FakePartition(
            List.of(lag(500), lag(10)), List.of(LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = reclaim.awaitCaughtUp(List.of(partition)).join().getFirst();

    // then
    assertThat(report.outcome()).isEqualTo(Outcome.CAUGHT_UP);
    assertThat(report.answers()).isEmpty();
  }

  private PartitionReclaim reclaimed(final FakePartition partition) {
    final var reports = reclaim.reclaim(List.of(partition));
    assertThat(reports).succeedsWithin(TIMEOUT);
    return reports.join().getFirst();
  }

  private static ReplayLag lag(final long lag) {
    return new ReplayLag(lag, 5000);
  }

  private static final class FakePartition implements ReclaimPartition {
    private final Deque<ReplayLag> lags;
    private final Deque<LeadershipTransferResult> answers;
    private final AtomicInteger lagReadsBeforeRequest = new AtomicInteger();
    private volatile boolean asked;
    private volatile boolean leads;
    private volatile int uninstalledReads;

    private FakePartition(
        final List<ReplayLag> lags, final List<LeadershipTransferResult> answers) {
      this.lags = new ArrayDeque<>(lags);
      this.answers = new ArrayDeque<>(answers);
    }

    @Override
    public int partition() {
      return 1;
    }

    @Override
    public boolean leads() {
      return leads;
    }

    @Override
    public synchronized CompletableFuture<ReplayLag> replayLag() {
      if (uninstalledReads > 0) {
        uninstalledReads--;
        return CompletableFuture.failedFuture(new IllegalStateException("not installed"));
      }
      if (!asked) {
        lagReadsBeforeRequest.incrementAndGet();
      }
      return CompletableFuture.completedFuture(lags.size() > 1 ? lags.poll() : lags.peek());
    }

    @Override
    public synchronized CompletableFuture<LeadershipTransferResult> requestLeadership(
        final Duration bound) {
      asked = true;
      final var answer = answers.size() > 1 ? answers.poll() : answers.peek();
      if (answer == LeadershipTransferResult.TRANSFERRED) {
        leads = true;
      }
      return CompletableFuture.completedFuture(answer);
    }
  }
}
