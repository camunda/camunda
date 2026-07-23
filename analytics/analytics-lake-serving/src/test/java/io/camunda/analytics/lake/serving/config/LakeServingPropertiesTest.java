/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LakeServingPropertiesTest {

  @Test
  void shouldRejectMissingWarehouseDir() {
    assertThatThrownBy(() -> new LakeServingProperties(null, null, 500, 15))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lake.serving.warehouse-dir");
  }

  @Test
  void shouldRejectBlankWarehouseDir() {
    assertThatThrownBy(() -> new LakeServingProperties("   ", null, 500, 15))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldResolveWarehouseAndStateDirPaths() {
    final LakeServingProperties properties =
        new LakeServingProperties("/tmp/warehouse", "/tmp/state", 500, 15);

    assertThat(properties.warehouseDirPath()).isEqualTo(Path.of("/tmp/warehouse"));
    assertThat(properties.stateDirPath()).contains(Path.of("/tmp/state"));
  }

  @Test
  void shouldTreatMissingStateDirAsAbsent() {
    final LakeServingProperties properties =
        new LakeServingProperties("/tmp/warehouse", null, 500, 15);

    assertThat(properties.stateDirPath()).isEmpty();
  }
}
