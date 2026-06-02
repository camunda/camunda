/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalyticsExporterConfigTest {

  @Test
  void shouldRejectBlankEndpoint() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setEndpoint("").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("endpoint");
  }

  @Test
  void shouldRejectInvalidHeartbeatInterval() {
    assertThatThrownBy(
            () -> new AnalyticsExporterConfig().setHeartbeatInterval("not-a-duration").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("heartbeatInterval");
  }

  @Test
  void shouldRejectNonPositiveHeartbeatInterval() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setHeartbeatInterval("PT0S").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }
}
