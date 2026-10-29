/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.Attempt;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.HandoverPartition;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.Outcome;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.Report;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class PartitionLeadershipHandoverTest {

  private static final MemberId LOCAL = MemberId.from("0");
  private static final MemberId FIRST = MemberId.from("1");
  private static final MemberId SECOND = MemberId.from("2");
  private static final Comparator<MemberId> IN_ID_ORDER = MemberId.ID_COMPARATOR;
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final PartitionLeadershipHandover handover =
      new PartitionLeadershipHandover(LOCAL, Duration.ofSeconds(5));

  @Test
  void shouldHandOverToTheMostPreferredReplica() {
    // given
    final var partition = leading(1, Map.of(SECOND, LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER.reversed());

    // then
    assertThat(report.partitions())
        .singleElement()
        .satisfies(
            handedOver -> {
              assertThat(handedOver.outcome()).isEqualTo(Outcome.TRANSFERRED);
              assertThat(handedOver.newLeader()).isEqualTo(SECOND);
            });
    assertThat(partition.asked).containsExactly(SECOND);
  }

  @Test
  void shouldTryTheNextReplicaWhenOneIsNotCaughtUp() {
    // given
    final var partition =
        leading(
            1,
            Map.of(
                FIRST, LeadershipTransferResult.LAG_TOO_HIGH,
                SECOND, LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER);

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.TRANSFERRED);
    assertThat(report.partitions().getFirst().newLeader()).isEqualTo(SECOND);
    assertThat(report.partitions().getFirst().attempts())
        .containsExactly(
            new Attempt(FIRST, LeadershipTransferResult.LAG_TOO_HIGH),
            new Attempt(SECOND, LeadershipTransferResult.TRANSFERRED));
  }

  @Test
  void shouldReportWhyNoReplicaTookOver() {
    // given
    final var partition =
        leading(
            1,
            Map.of(
                FIRST, LeadershipTransferResult.UNREACHABLE,
                SECOND, LeadershipTransferResult.REPLICATION_TIMED_OUT));

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER);

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.NO_REPLICA_TOOK_OVER);
    assertThat(report.fallbacks()).contains("NO_REPLICA_TOOK_OVER");
  }

  @Test
  void shouldFallBackForASingleReplica() {
    // given
    final var partition = new FakePartition(1, true, List.of(LOCAL), Map.of());

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER);

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.NO_OTHER_REPLICA);
    assertThat(partition.asked).isEmpty();
  }

  @Test
  void shouldNotTryAnotherReplicaWhenTheLeaderRefuses() {
    // given
    final var partition =
        leading(1, Map.of(FIRST, LeadershipTransferResult.CONFIGURATION_CHANGE_IN_PROGRESS));

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER);

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.REFUSED);
    assertThat(partition.asked).containsExactly(FIRST);
  }

  @Test
  void shouldLeavePartitionsItDoesNotLeadAlone() {
    // given
    final var partition = new FakePartition(1, false, List.of(LOCAL, FIRST, SECOND), Map.of());

    // when
    final var report = handOver(List.of(partition), IN_ID_ORDER);

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.NOT_LEADER);
    assertThat(report.fallbacks()).isEmpty();
    assertThat(partition.asked).isEmpty();
  }

  @Test
  void shouldGiveUpOnceTheBoundPassed() {
    // given
    final var bounded = new PartitionLeadershipHandover(LOCAL, Duration.ofMillis(100));
    final var partition = new FakePartition(1, true, List.of(LOCAL, FIRST), Map.of());
    partition.pending = new CompletableFuture<>();

    // when
    final var report = bounded.handOver(List.of(partition), IN_ID_ORDER).join();

    // then
    assertThat(report.partitions().getFirst().outcome()).isEqualTo(Outcome.TIMED_OUT);
  }

  @Test
  void shouldHandOverEveryLedPartition() {
    // given
    final var first = leading(1, Map.of(FIRST, LeadershipTransferResult.TRANSFERRED));
    final var second = leading(2, Map.of(FIRST, LeadershipTransferResult.TRANSFERRED));

    // when
    final var report = handOver(List.of(first, second), IN_ID_ORDER);

    // then
    assertThat(report.partitions())
        .extracting(PartitionLeadershipHandover.PartitionReport::outcome)
        .containsExactly(Outcome.TRANSFERRED, Outcome.TRANSFERRED);
  }

  private Report handOver(
      final List<FakePartition> partitions, final Comparator<MemberId> preference) {
    return handover
        .handOver(partitions, preference)
        .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .join();
  }

  private static FakePartition leading(
      final int number, final Map<MemberId, LeadershipTransferResult> results) {
    return new FakePartition(number, true, List.of(LOCAL, FIRST, SECOND), results);
  }

  private static final class FakePartition implements HandoverPartition {
    private final PartitionId id;
    private final List<MemberId> members;
    private final Map<MemberId, LeadershipTransferResult> results;
    private final List<MemberId> asked = new ArrayList<>();
    private boolean leads;
    private CompletableFuture<LeadershipTransferResult> pending;

    private FakePartition(
        final int number,
        final boolean leads,
        final List<MemberId> members,
        final Map<MemberId, LeadershipTransferResult> results) {
      id = new PartitionId("tenant", number);
      this.leads = leads;
      this.members = members;
      this.results = new HashMap<>(results);
    }

    @Override
    public PartitionId id() {
      return id;
    }

    @Override
    public boolean leads() {
      return leads;
    }

    @Override
    public Collection<MemberId> members() {
      return members;
    }

    @Override
    public CompletableFuture<LeadershipTransferResult> transferLeadership(
        final MemberId desiredLeader) {
      asked.add(desiredLeader);
      if (pending != null) {
        return pending;
      }
      final var result =
          results.getOrDefault(desiredLeader, LeadershipTransferResult.NOT_REPLICATING);
      if (result == LeadershipTransferResult.TRANSFERRED) {
        leads = false;
      }
      return CompletableFuture.completedFuture(result);
    }
  }
}
