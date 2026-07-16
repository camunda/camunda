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
    final FlowMetrics metrics = FlowMetrics.of(null, "projection");

    // then the NOOP singleton is returned and every recording is a harmless no-op
    assertThat(metrics).isSameAs(FlowMetrics.NOOP);
    metrics.countRecordProcessed();
    metrics.countDedupSkipped();
    metrics.countSegmentSealed();
    metrics.countDeltaMerged();
  }

  @Test
  void shouldRegisterCountersTaggedByStage() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FlowMetrics metrics = FlowMetrics.of(registry, "projection");

    // when
    metrics.countRecordProcessed();
    metrics.countRecordProcessed();
    metrics.countDedupSkipped();
    metrics.countSegmentSealed();
    metrics.countDeltaMerged();

    // then each meter is registered once, tagged by the given stage, and moved exactly as recorded
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "projection")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry.get("eb.streaming.dedup.skipped").tag("stage", "projection").counter().count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("eb.streaming.segments.sealed")
                .tag("stage", "projection")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry.get("eb.streaming.deltas.merged").tag("stage", "projection").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldKeepStagesSeparateWhenSharingOneRegistry() {
    // given two stages sharing one registry, as the pipeline does
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final FlowMetrics projection = FlowMetrics.of(registry, "projection");
    final FlowMetrics aggregation = FlowMetrics.of(registry, "aggregation");

    // when
    projection.countRecordProcessed();
    aggregation.countRecordProcessed();
    aggregation.countRecordProcessed();

    // then each stage's counter moves independently
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "projection")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("eb.streaming.records.processed")
                .tag("stage", "aggregation")
                .counter()
                .count())
        .isEqualTo(2.0);
  }
}
