/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DatasetRegistryTest {

  private static DatasetDeclaration declaration(final String name) {
    return DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
        .meter(Meter.of("n", MeterCatalog.COUNT))
        .window(60_000L)
        .build();
  }

  @Test
  void shouldAdmitWithStableCubeIdsAndFrozenActivation() {
    // given
    final DatasetRegistry registry = new DatasetRegistry();
    final Map<Integer, Long> activation = new HashMap<>(Map.of(0, 500L, 1, 900L));

    // when
    final RegisteredDataset first = registry.admit(declaration("a"), activation);
    final RegisteredDataset second = registry.admit(declaration("b"), Map.of(0, 10L));

    // then cube ids are stable and monotonic
    assertThat(first.cubeId()).isEqualTo(1L);
    assertThat(second.cubeId()).isEqualTo(2L);
    // and the activation vector is frozen — mutating the input afterward does not change it
    activation.put(0, 999L);
    assertThat(first.activation()).containsEntry(0, 500L);
  }

  @Test
  void shouldApplyForwardOnlyActivation() {
    // given a cube activated at position 500 on partition 0 (and nothing declared for partition 2)
    final DatasetRegistry registry = new DatasetRegistry();
    final RegisteredDataset cube = registry.admit(declaration("a"), Map.of(0, 500L));

    // then facts before activation are excluded, at/after are included
    assertThat(cube.admits(0, 499L)).isFalse();
    assertThat(cube.admits(0, 500L)).isTrue();
    assertThat(cube.admits(0, 600L)).isTrue();
    // a partition with no declared activation is active from its start
    assertThat(cube.admits(2, 0L)).isTrue();
  }

  @Test
  void shouldRecoverIdenticallyViaSnapshotRestore() {
    // given a registry with two admitted cubes
    final DatasetRegistry before = new DatasetRegistry();
    before.admit(declaration("a"), Map.of(0, 500L));
    before.admit(declaration("b"), Map.of(0, 10L));

    // when restored from its snapshot (a cold recovery)
    final DatasetRegistry after = DatasetRegistry.restore(before.snapshot());

    // then the cubes, ids and activation are identical
    assertThat(after.active())
        .extracting(RegisteredDataset::cubeId, RegisteredDataset::activation)
        .containsExactly(tuple(1L, Map.of(0, 500L)), tuple(2L, Map.of(0, 10L)));
    // and a new admission continues past the restored ids (no reuse)
    assertThat(after.admit(declaration("c"), Map.of()).cubeId()).isEqualTo(3L);
  }
}
