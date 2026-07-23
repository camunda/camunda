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
import java.util.Map;
import org.junit.jupiter.api.Test;

class LakeServingPropertiesTest {

  @Test
  void shouldRejectMissingWarehouseDir() {
    assertThatThrownBy(() -> new LakeServingProperties(null, null, 500, 15, 5_000_000, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lake.serving.warehouse-dir");
  }

  @Test
  void shouldRejectBlankWarehouseDir() {
    assertThatThrownBy(() -> new LakeServingProperties("   ", null, 500, 15, 5_000_000, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldResolveWarehouseAndStateDirPaths() {
    final LakeServingProperties properties =
        new LakeServingProperties("/tmp/warehouse", "/tmp/state", 500, 15, 5_000_000, null);

    assertThat(properties.warehouseDirPath()).isEqualTo(Path.of("/tmp/warehouse"));
    assertThat(properties.stateDirPath()).contains(Path.of("/tmp/state"));
  }

  @Test
  void shouldTreatMissingStateDirAsAbsent() {
    final LakeServingProperties properties =
        new LakeServingProperties("/tmp/warehouse", null, 500, 15, 5_000_000, null);

    assertThat(properties.stateDirPath()).isEmpty();
  }

  @Test
  void shouldOverlayDefaultDimKindsWithoutDroppingUnsetOnes() {
    final LakeServingProperties properties =
        new LakeServingProperties(
            "/tmp/warehouse", null, 500, 15, 5_000_000, Map.of("version", "RELEASE_VERSION"));

    final Map<String, String> effective = properties.effectiveDimKinds();

    assertThat(effective).containsEntry("version", "RELEASE_VERSION");
    assertThat(effective).containsEntry("process_id", "PROCESS");
    assertThat(effective).containsEntry("variant_hash", "VARIANT");
  }

  @Test
  void shouldUseDefaultDimKindsWhenUnset() {
    final LakeServingProperties properties =
        new LakeServingProperties("/tmp/warehouse", null, 500, 15, 5_000_000, null);

    final Map<String, String> effective = properties.effectiveDimKinds();

    assertThat(effective)
        .containsEntry("variant_hash", "VARIANT")
        .containsEntry("flow_id", "FLOW")
        .containsEntry("source_element_id", "ELEMENT")
        .containsEntry("target_element_id", "ELEMENT")
        .containsEntry("element_id", "ELEMENT")
        .containsEntry("version", "VERSION")
        .containsEntry("process_id", "PROCESS")
        .containsEntry("object_type", "OBJECT_TYPE");
  }
}
