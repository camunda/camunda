/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionRebalanceState;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class ClusterRebalanceMetricsTest {

  private static final String GROUP = "default";
  private static final PartitionId PARTITION_1 = new PartitionId(GROUP, 1);
  private static final PartitionId PARTITION_2 = new PartitionId(GROUP, 2);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ClusterRebalanceMetrics metrics = new ClusterRebalanceMetrics(registry);

  @Test
  void shouldReplaceTheStatesOfThePreviousRebalance() {
    // given
    metrics.setPartitionStates(
        Map.of(
            PARTITION_1, PartitionRebalanceState.TRANSFERRED,
            PARTITION_2, PartitionRebalanceState.FAILED));

    // when
    metrics.setPartitionStates(Map.of(PARTITION_1, PartitionRebalanceState.PENDING));

    // then
    assertThat(state(1)).isEqualTo(1);
    assertThat(gauge(2)).isNull();
  }

  @Test
  void shouldReportCancelledAsItsOwnGaugeValue() {
    // when
    metrics.setPartitionStates(Map.of(PARTITION_1, PartitionRebalanceState.CANCELLED));

    // then
    assertThat(state(1)).isEqualTo(6);
  }

  @Test
  void shouldStopReportingStatesOnceItStopsCoordinating() {
    // given
    metrics.setPartitionStates(Map.of(PARTITION_1, PartitionRebalanceState.TRANSFERRED));

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
}
