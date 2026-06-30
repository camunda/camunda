/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocksDbBaseProjectionStoreTest {

  @TempDir private Path dataDir;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldPersistAndReadBackProjection() throws Exception {
    // given
    final ProcessInstanceProjection projection =
        new ProcessInstanceProjection(
            123L, 77L, "order", 3, "<default>", 1000L, 1500L, false, true);

    // when / then
    try (final RocksDbBaseProjectionStore store =
        RocksDbBaseProjectionStore.open(dataDir.toFile(), meterRegistry)) {
      store.put(projection);
      assertThat(store.get(123L)).contains(projection);
      assertThat(store.get(999L)).isEmpty();
    }
  }

  @Test
  void shouldTrackConsumedPosition() throws Exception {
    try (final RocksDbBaseProjectionStore store =
        RocksDbBaseProjectionStore.open(dataDir.toFile(), meterRegistry)) {
      assertThat(store.getConsumedPosition()).isEqualTo(BaseProjectionStore.NO_POSITION);
      store.setConsumedPosition(42L);
      assertThat(store.getConsumedPosition()).isEqualTo(42L);
    }
  }

  @Test
  void shouldSurviveReopen() throws Exception {
    // given — a projection and position written, then the store closed
    final ProcessInstanceProjection projection =
        new ProcessInstanceProjection(7L, 5L, "payment", 1, "tenant-x", 100L, 300L, false, true);
    try (final RocksDbBaseProjectionStore store =
        RocksDbBaseProjectionStore.open(dataDir.toFile(), meterRegistry)) {
      store.put(projection);
      store.setConsumedPosition(55L);
    }

    // when — reopened from the same directory
    try (final RocksDbBaseProjectionStore reopened =
        RocksDbBaseProjectionStore.open(dataDir.toFile(), meterRegistry)) {
      // then
      assertThat(reopened.get(7L)).contains(projection);
      assertThat(reopened.getConsumedPosition()).isEqualTo(55L);
    }
  }
}
