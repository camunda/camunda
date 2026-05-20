/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalyticsExporterContextTest {

  @Test
  void shouldComputeDeterministicFingerprint() {
    // given / when
    final var ctx1 = AnalyticsExporterContext.create("test-license", "cluster-1", 1);
    final var ctx2 = AnalyticsExporterContext.create("test-license", "cluster-1", 1);

    // then
    assertThat(ctx1.fingerprint())
        .isEqualTo(ctx2.fingerprint())
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void shouldProduceDifferentFingerprintsForDifferentLicenses() {
    // given / when
    final var ctx1 = AnalyticsExporterContext.create("license-a", "cluster-1", 1);
    final var ctx2 = AnalyticsExporterContext.create("license-b", "cluster-1", 1);

    // then
    assertThat(ctx1.fingerprint()).isNotEqualTo(ctx2.fingerprint());
  }

  @Test
  void shouldRejectMissingLicenseKey() {
    // when / then
    assertThatThrownBy(() -> AnalyticsExporterContext.create(null, "cluster-1", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }

  @Test
  void shouldRejectBlankLicenseKey() {
    // when / then
    assertThatThrownBy(() -> AnalyticsExporterContext.create("  ", "cluster-1", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }
}
