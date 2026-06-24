/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThatCode;
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

  @Test
  void shouldRejectInvalidPushInterval() {
    assertThatThrownBy(
            () -> new AnalyticsExporterConfig().setPushInterval("not-a-duration").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pushInterval");
  }

  @Test
  void shouldRejectNonPositiveMaxQueueSize() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setMaxQueueSize(0).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxQueueSize");
  }

  @Test
  void shouldRejectNonPositiveMaxBatchSize() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setMaxBatchSize(0).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBatchSize");
  }

  @Test
  void shouldRejectMaxBatchSizeExceedingMaxQueueSize() {
    assertThatThrownBy(
            () ->
                new AnalyticsExporterConfig().setMaxQueueSize(100).setMaxBatchSize(200).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBatchSize")
        .hasMessageContaining("maxQueueSize");
  }

  @Test
  void shouldRejectSamplingRateBelowZero() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate(-0.1).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldRejectSamplingRateNaN() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate(Double.NaN).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldRejectSamplingRateAboveOne() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate(1.1).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldAcceptSamplingRateBoundaries() {
    assertThatCode(() -> new AnalyticsExporterConfig().setSamplingRate(0.0).validate())
        .doesNotThrowAnyException();
    assertThatCode(() -> new AnalyticsExporterConfig().setSamplingRate(1.0).validate())
        .doesNotThrowAnyException();
  }
}
