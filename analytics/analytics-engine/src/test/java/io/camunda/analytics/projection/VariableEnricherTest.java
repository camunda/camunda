/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class VariableEnricherTest {

  private static final Map<String, String> AT_CREATE = Map.of("region", "EU");
  private static final Map<String, String> AT_EVENT = Map.of("region", "US");
  private static final Map<String, String> AT_COMPLETE = Map.of("region", "APAC");

  @Test
  void shouldSelectSnapshotPerTiming() {
    assertThat(
            VariableEnricher.select(EnrichmentTiming.EVENT_TIME, AT_CREATE, AT_EVENT, AT_COMPLETE))
        .isEqualTo(AT_EVENT);
    assertThat(
            VariableEnricher.select(EnrichmentTiming.PI_CREATE, AT_CREATE, AT_EVENT, AT_COMPLETE))
        .isEqualTo(AT_CREATE);
    assertThat(
            VariableEnricher.select(EnrichmentTiming.PI_COMPLETE, AT_CREATE, AT_EVENT, AT_COMPLETE))
        .isEqualTo(AT_COMPLETE);
  }

  @Test
  void shouldResolveMissingSnapshotToEmpty() {
    // e.g. the completion snapshot is not yet available when the event is folded
    assertThat(VariableEnricher.select(EnrichmentTiming.PI_COMPLETE, AT_CREATE, AT_EVENT, null))
        .isEmpty();
  }
}
