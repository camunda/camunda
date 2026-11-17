/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class ClusterRebalanceMetricsTest {

  private static final String GROUP = "default";
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ClusterRebalanceMetrics metrics = new ClusterRebalanceMetrics(registry);

  @Test
  void shouldReplaceTheStatesOfThePreviousRebalance() {
    // given
    metrics.setPartitionStates(
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2).transferred(),
            PartitionRebalance.pending(GROUP, 2, MEMBER_1, MEMBER_2)
                .completed(PartitionRebalanceOutcome.NO_RESPONSE)));

    // when
    metrics.setPartitionStates(List.of(PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2)));

    // then
    assertThat(state(1)).isEqualTo(1);
    assertThat(gauge(2)).isNull();
  }

  @Test
  void shouldReportCompletedAsItsOwnGaugeValueRegardlessOfOutcome() {
    // when
    metrics.setPartitionStates(
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2)
                .completed(PartitionRebalanceOutcome.CANCELLED)));

    // then
    assertThat(state(1)).isEqualTo(3);
  }

  @Test
  void shouldStopReportingStatesOnceItStopsCoordinating() {
    // given
    metrics.setPartitionStates(
        List.of(PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2).transferred()));

    // when
    metrics.stopCoordinating();

    // then
    assertThat(gauge(1)).isNull();
  }

  @Test
  void shouldReportEveryOutcomeAsSoonAsItCoordinates() {
    // when
    metrics.startCoordinating();

    // then
    assertThat(registry.find("zeebe.cluster.rebalance.elapsed").timers())
        .extracting(timer -> timer.getId().getTag("result"))
        .containsExactlyInAnyOrder("COMPLETED", "CANCELLED", "FAILED");
  }

  @Test
  void shouldDeregisterEveryCoordinatorOwnedMeterWhenCoordinationStops() {
    // given
    metrics.setPartitionStates(
        List.of(PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2).transferred()));
    metrics.observePartitionDuration(
        new PartitionId(GROUP, 1), "TRANSFERRED", Duration.ofSeconds(1));
    metrics.observeElapsed(RebalanceOutcome.COMPLETED, Duration.ofSeconds(1));

    // when
    metrics.stopCoordinating();

    // then
    assertThat(gauge(1)).isNull();
    assertThat(registry.find("zeebe.cluster.rebalance.partition.duration").timers()).isEmpty();
    assertThat(registry.find("zeebe.cluster.rebalance.elapsed").timers()).isEmpty();
  }

  @Test
  void shouldNotReuseStaleMeterMapEntriesAfterCoordinationRestarts() {
    // given
    metrics.observePartitionDuration(
        new PartitionId(GROUP, 1), "TRANSFERRED", Duration.ofSeconds(1));
    metrics.observeElapsed(RebalanceOutcome.COMPLETED, Duration.ofSeconds(1));
    metrics.stopCoordinating();

    // when
    metrics.observePartitionDuration(
        new PartitionId(GROUP, 1), "TRANSFERRED", Duration.ofSeconds(2));
    metrics.observeElapsed(RebalanceOutcome.COMPLETED, Duration.ofSeconds(2));

    // then
    assertThat(partitionDurationCount(1, "TRANSFERRED")).isEqualTo(1);
    assertThat(registry.get("zeebe.cluster.rebalance.elapsed").timers()).hasSize(1);
    assertThat(
            registry
                .get("zeebe.cluster.rebalance.elapsed")
                .tag("result", RebalanceOutcome.COMPLETED.name())
                .timer()
                .count())
        .isEqualTo(1);
  }

  private double state(final int partitionId) {
    return registry
        .get("zeebe.cluster.rebalance.partition.state")
        .tag("partition", String.valueOf(partitionId))
        .tag("physicalTenant", GROUP)
        .gauge()
        .value();
  }

  private @Nullable Gauge gauge(final int partitionId) {
    return registry
        .find("zeebe.cluster.rebalance.partition.state")
        .tag("partition", String.valueOf(partitionId))
        .gauge();
  }

  private long partitionDurationCount(final int partitionId, final String result) {
    return registry
        .get("zeebe.cluster.rebalance.partition.duration")
        .tag("partition", String.valueOf(partitionId))
        .tag("physicalTenant", GROUP)
        .tag("result", result)
        .timer()
        .count();
  }
}
