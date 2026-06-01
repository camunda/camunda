/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyticsExporterMetadataTest {

  @Test
  void shouldSerializeAndDeserializeAllFields() {
    // given
    final var metadata = new AnalyticsExporterMetadata(10, 42);

    // when
    final var restored = AnalyticsExporterMetadata.deserialize(metadata.serialize());

    // then
    assertThat(restored.getEventSequenceNumber()).isEqualTo(10);
    assertThat(restored.getMetricSequenceNumber()).isEqualTo(42);
  }

  @Test
  void shouldDeserializeMetadataWithoutMetricSequenceNumber() {
    // given
    final var oldMetadata = new AnalyticsExporterMetadata(5, 0);

    // when
    final var restored = AnalyticsExporterMetadata.deserialize(oldMetadata.serialize());

    // then
    assertThat(restored.getEventSequenceNumber()).isEqualTo(5);
    assertThat(restored.getMetricSequenceNumber()).isZero();
  }

  @Test
  void shouldIncrementMetricSequenceNumber() {
    // given
    final var metadata = new AnalyticsExporterMetadata(0, 10);

    // when / then
    assertThat(metadata.incrementAndGetMetricSequenceNumber()).isEqualTo(11);
    assertThat(metadata.incrementAndGetMetricSequenceNumber()).isEqualTo(12);
  }
}
