/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import org.junit.jupiter.api.Test;

final class OsNamesTest {

  private final DimensionSchema grain =
      DimensionSchema.of(
          new DimensionColumn("bpmnProcessId", DimensionType.STRING),
          new DimensionColumn("var.region", DimensionType.STRING));

  @Test
  void shouldValidateFieldsAndRejectInjection() {
    assertThat(OsNames.field("bpmnProcessId")).isEqualTo("bpmnProcessId");
    assertThat(OsNames.field("var.region")).isEqualTo("var_region");
    assertThatThrownBy(() -> OsNames.field("a b")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldDeriveStableCellIdIncludingWindowAndTier() {
    final DimensionKey key = DimensionKey.of(grain, "orders", "EU");
    assertThat(OsNames.cellId(key, 0L, 60_000L)).isEqualTo(OsNames.cellId(key, 0L, 60_000L));
    assertThat(OsNames.cellId(key, 0L, 60_000L))
        .isNotEqualTo(OsNames.cellId(key, 60_000L, 60_000L));
  }

  @Test
  void shouldRoundTripBase64() {
    final byte[] bytes = {1, 2, 3, 4, 5};
    assertThat(OsNames.decode(OsNames.encode(bytes))).containsExactly(bytes);
  }
}
