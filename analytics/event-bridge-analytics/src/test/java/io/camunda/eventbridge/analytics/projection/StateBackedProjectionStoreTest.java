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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StateBackedProjectionStoreTest {

  @TempDir private Path dataDir;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldAccumulateAndDeleteVariables() throws Exception {
    try (final StateBackedProjectionStore store =
        StateBackedProjectionStore.rocksDb(dataDir.toFile(), meterRegistry)) {
      // given — variables accumulate per instance (last write wins)
      store.putVariable(123L, "region", "EU");
      store.putVariable(123L, "priority", "high");
      store.putVariable(123L, "region", "US");

      // then
      assertThat(store.getVariables(123L))
          .containsEntry("region", "US")
          .containsEntry("priority", "high");
      assertThat(store.getVariables(999L)).isEmpty();

      // when — deleted on completion
      store.deleteVariables(123L);
      assertThat(store.getVariables(123L)).isEmpty();
    }
  }

  @Test
  void shouldTrackConsumedPositionPerPartition() throws Exception {
    try (final StateBackedProjectionStore store =
        StateBackedProjectionStore.rocksDb(dataDir.toFile(), meterRegistry)) {
      assertThat(store.getConsumedPosition(1)).isEqualTo(BaseProjectionStore.NO_POSITION);
      store.setConsumedPosition(1, 42L);
      store.setConsumedPosition(2, 99L);
      assertThat(store.getConsumedPosition(1)).isEqualTo(42L);
      assertThat(store.getConsumedPosition(2)).isEqualTo(99L);
      assertThat(store.consumedPositions()).containsOnly(Map.entry(1, 42L), Map.entry(2, 99L));
    }
  }

  @Test
  void shouldSurviveReopen() throws Exception {
    // given — variables and a position written, then the store closed
    try (final StateBackedProjectionStore store =
        StateBackedProjectionStore.rocksDb(dataDir.toFile(), meterRegistry)) {
      store.putVariable(7L, "region", "US");
      store.setConsumedPosition(1, 55L);
    }

    // when — reopened from the same directory
    try (final StateBackedProjectionStore reopened =
        StateBackedProjectionStore.rocksDb(dataDir.toFile(), meterRegistry)) {
      // then
      assertThat(reopened.getVariables(7L)).containsEntry("region", "US");
      assertThat(reopened.getConsumedPosition(1)).isEqualTo(55L);
    }
  }
}
