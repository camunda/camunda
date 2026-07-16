/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class FlowMetricsTest {

  @Test
  void shouldReturnNoopWhenRegistryIsNull() {
    // given / when
    final FlowMetrics metrics = FlowMetrics.of(null, "projection", 1);

    // then the NOOP singleton is returned and every recording is a harmless no-op
    assertThat(metrics).isSameAs(FlowMetrics.NOOP);
    metrics.countRecordProcessed();
    metrics.countDedupSkipped();
    metrics.countSegmentSealed();
    assertThat(metrics.deltaMergedCounter(60_000L)).isSameAs(FlowMetrics.NOOP_DELTA_COUNTER);
    metrics.deltaMergedCounter(60_000L).run();
  }

  @Test
  void shouldRegisterCountersTaggedByStageAndPartition() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FlowMetrics metrics = FlowMetrics.of(registry, "projection", 3);

    // when
    metrics.countRecordProcessed();
    metrics.countRecordProcessed();
    metrics.countDedupSkipped();
    metrics.countSegmentSealed();

    // then each meter is registered once, tagged by stage AND partition (a single wedged
    // partition must be visible, not averaged into the healthy partitions' aggregate)
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "projection")
                .tag("partition", "3")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .get("eb.streaming.dedup.skipped")
                .tag("stage", "projection")
                .tag("partition", "3")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("eb.streaming.segments.sealed")
                .tag("stage", "projection")
                .tag("partition", "3")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldCountMergedDeltasPerTier() {
    // given two tiers' pre-resolved recorders, as one cube's minute and hour mergers hold them
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FlowMetrics metrics = FlowMetrics.of(registry, "aggregation", 1);
    final Runnable minuteTier = metrics.deltaMergedCounter(60_000L);
    final Runnable hourTier = metrics.deltaMergedCounter(3_600_000L);

    // when one composite delta rolls into both tiers
    minuteTier.run();
    hourTier.run();

    // then each tier's counter moves independently — no cross-tier inflation
    assertThat(
            registry
                .get("eb.streaming.deltas.merged")
                .tag("stage", "aggregation")
                .tag("partition", "1")
                .tag("tier", "60000")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("eb.streaming.deltas.merged")
                .tag("stage", "aggregation")
                .tag("partition", "1")
                .tag("tier", "3600000")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldKeepPartitionsSeparateWhenSharingOneRegistry() {
    // given two partition tasks of one stage sharing one registry, as the pipeline does
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FlowMetrics partition1 = FlowMetrics.of(registry, "projection", 1);
    final FlowMetrics partition2 = FlowMetrics.of(registry, "projection", 2);

    // when
    partition1.countRecordProcessed();
    partition2.countRecordProcessed();
    partition2.countRecordProcessed();

    // then each partition's counter moves independently — a wedged partition stays visible
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "projection")
                .tag("partition", "1")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "projection")
                .tag("partition", "2")
                .counter()
                .count())
        .isEqualTo(2.0);
  }
}
