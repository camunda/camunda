/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;

/**
 * Configure-time validation tests that require the {@code CAMUNDA_LICENSE_KEY} environment variable
 * to be absent. Kept in a dedicated class (without the shared {@code @BeforeEach} that builds a
 * configured exporter) so clearing the variable does not break exporter set-up.
 */
class AnalyticsExporterConfigureTest {

  @Test
  @ClearEnvironmentVariable(key = "CAMUNDA_LICENSE_KEY")
  void shouldRejectMissingLicenseKey() {
    // given
    final var context =
        new ExporterTestContext()
            .setConfiguration(
                new ExporterTestConfiguration<>("analytics", new AnalyticsExporterConfig()));

    // when / then
    assertThatThrownBy(() -> new AnalyticsExporter().configure(context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAMUNDA_LICENSE_KEY");
  }
}
