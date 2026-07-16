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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class StoreMetricsTest {

  @Test
  void shouldReturnNoopWhenRegistryIsNull() {
    // given / when
    final StoreMetrics metrics = StoreMetrics.of(null, "projection", 1);

    // then the NOOP singleton is returned and binding is a harmless no-op
    assertThat(metrics).isSameAs(StoreMetrics.NOOP);
    metrics.bindOverlay("elements", () -> 1L, () -> 2L);
  }

  @Test
  void shouldBindOverlayGaugesReadingTheSuppliersLazily() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final StoreMetrics metrics = StoreMetrics.of(registry, "projection", 1);
    final AtomicLong entries = new AtomicLong(3);
    final AtomicLong bytes = new AtomicLong(4096);

    // when
    metrics.bindOverlay("elements", entries::get, bytes::get);

    // then the gauges read the live supplier at scrape time, tagged by stage/partition/store
    assertThat(entriesGauge(registry)).isEqualTo(3.0);
    assertThat(bytesGauge(registry)).isEqualTo(4096.0);

    // when the underlying values change without any re-registration
    entries.set(9);
    bytes.set(1);

    // then the gauges reflect the new reading — no allocation, no re-binding needed
    assertThat(entriesGauge(registry)).isEqualTo(9.0);
    assertThat(bytesGauge(registry)).isEqualTo(1.0);
  }

  @Test
  void shouldKeepStoresSeparateWhenSharingOneStageAndPartition() {
    // given two stores bound under the same stage/partition (as one task's several caches are)
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final StoreMetrics metrics = StoreMetrics.of(registry, "projection", 1);

    // when
    metrics.bindOverlay("elements", () -> 5L, () -> 50L);
    metrics.bindOverlay("variables", () -> 7L, () -> 70L);

    // then each store's gauge is independent
    assertThat(entriesGauge(registry)).isEqualTo(5.0);
    assertThat(
            registry
                .get("eb.streaming.store.overlay.entries")
                .tag("stage", "projection")
                .tag("partition", "1")
                .tag("store", "variables")
                .gauge()
                .value())
        .isEqualTo(7.0);
  }

  private static double entriesGauge(final SimpleMeterRegistry registry) {
    return registry
        .get("eb.streaming.store.overlay.entries")
        .tag("stage", "projection")
        .tag("partition", "1")
        .tag("store", "elements")
        .gauge()
        .value();
  }

  private static double bytesGauge(final SimpleMeterRegistry registry) {
    return registry
        .get("eb.streaming.store.overlay.bytes")
        .tag("stage", "projection")
        .tag("partition", "1")
        .tag("store", "elements")
        .gauge()
        .value();
  }
}
