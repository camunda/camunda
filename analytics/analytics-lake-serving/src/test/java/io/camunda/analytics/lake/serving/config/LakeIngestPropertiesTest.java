/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LakeIngestPropertiesTest {

  private static LakeIngestProperties properties(
      final String stateDir, final String bpmnDir, final Long tombstoneRetentionMs) {
    return new LakeIngestProperties(
        true,
        "http://broker:8080",
        "zeebe-records",
        "lake-poc",
        stateDir,
        5000,
        30_000L,
        30_000L,
        300_000L,
        8091,
        bpmnDir,
        tombstoneRetentionMs);
  }

  @Test
  void shouldAnchorIngestToTheServedWarehouse() {
    // given
    final Path warehouseDir = Path.of("/data/warehouse/lake");

    // when
    final LakeConfig config = properties(null, null, null).toLakeConfig(warehouseDir);

    // then
    assertThat(config.warehouseDir()).isEqualTo(warehouseDir);
    assertThat(config.contactPoint()).isEqualTo("http://broker:8080");
    assertThat(config.topic()).isEqualTo("zeebe-records");
    assertThat(config.consumerGroup()).isEqualTo("lake-poc");
  }

  @Test
  void shouldDeriveStateDirAsWarehouseSiblingWhenUnset() {
    // given
    final Path warehouseDir = Path.of("/data/warehouse/lake");

    // when
    final LakeConfig config = properties(null, null, null).toLakeConfig(warehouseDir);

    // then
    assertThat(config.stateDir()).isEqualTo(Path.of("/data/warehouse/lake-state"));
  }

  @Test
  void shouldPreferExplicitStateDirOverDerivation() {
    // given
    final Path warehouseDir = Path.of("/data/warehouse/lake");

    // when
    final LakeConfig config =
        properties("/somewhere/else/state", null, null).toLakeConfig(warehouseDir);

    // then
    assertThat(config.stateDir()).isEqualTo(Path.of("/somewhere/else/state"));
  }

  @Test
  void shouldTreatBlankBpmnDirAsAbsent() {
    // given / when
    final LakeConfig config = properties(null, "  ", null).toLakeConfig(Path.of("/w/lake"));

    // then
    assertThat(config.bpmnDir()).isNull();
  }

  @Test
  void shouldDefaultTombstoneRetentionWhenUnset() {
    // given / when
    final LakeConfig config = properties(null, null, null).toLakeConfig(Path.of("/w/lake"));
    final LakeConfig overridden = properties(null, null, 42L).toLakeConfig(Path.of("/w/lake"));

    // then
    assertThat(config.objectTombstoneRetentionMs())
        .isEqualTo(LakeConfig.DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS);
    assertThat(overridden.objectTombstoneRetentionMs()).isEqualTo(42L);
  }
}
