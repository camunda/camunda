/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.exporter.analytics.sampling.HashSampler;
import org.junit.jupiter.api.Test;

class AnalyticsExporterConfigTest {

  @Test
  void shouldUseTelemetryEndpointByDefault() {
    assertThat(new AnalyticsExporterConfig().getEndpoint())
        .isEqualTo("https://telemetry.camunda.io");
  }

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
  void shouldRejectNonPositivePushInterval() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setPushInterval("PT0S").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  void shouldRejectNegativePushInterval() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setPushInterval("-PT5M").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  void shouldRejectInsecureEndpointByDefault() {
    assertThatThrownBy(
            () ->
                new AnalyticsExporterConfig()
                    .setEndpoint("http://analytics.example.com")
                    .validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https://");
  }

  @Test
  void shouldAllowInsecureEndpointWhenAllowInsecureIsTrue() {
    assertThatCode(
            () ->
                new AnalyticsExporterConfig()
                    .setEndpoint("http://analytics.example.com")
                    .setAllowInsecure(true)
                    .validate())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowHttpLocalhostWithoutAllowInsecure() {
    assertThatCode(
            () -> new AnalyticsExporterConfig().setEndpoint("http://localhost:8080").validate())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowHttp127001WithoutAllowInsecure() {
    assertThatCode(
            () -> new AnalyticsExporterConfig().setEndpoint("http://127.0.0.1:8080").validate())
        .doesNotThrowAnyException();
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
  void shouldRejectInvalidHttpConnectTimeout() {
    assertThatThrownBy(
            () -> new AnalyticsExporterConfig().setHttpConnectTimeout("not-a-duration").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("httpConnectTimeout");
  }

  @Test
  void shouldRejectNonPositiveHttpConnectTimeout() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setHttpConnectTimeout("PT0S").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  void shouldRejectInvalidHttpRequestTimeout() {
    assertThatThrownBy(
            () -> new AnalyticsExporterConfig().setHttpRequestTimeout("not-a-duration").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("httpRequestTimeout");
  }

  @Test
  void shouldRejectNonPositiveHttpRequestTimeout() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setHttpRequestTimeout("PT0S").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be positive");
  }

  @Test
  void shouldRejectNonPositiveHttpMaxRetryAttempts() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setHttpMaxRetryAttempts(0).validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("httpMaxRetryAttempts");
  }

  @Test
  void shouldRejectSamplingRateBelowZero() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate("-0.1").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldRejectSamplingRateNaN() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate("NaN").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldRejectSamplingRateAboveOne() {
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate("1.1").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate");
  }

  @Test
  void shouldRejectNonNumericSamplingRateWithRangeNamingMessage() {
    // given — a typo'd or otherwise non-numeric samplingRate (see
    // https://github.com/camunda/camunda/issues/62752); this must fail with the same
    // range-naming message as a numeric out-of-range value, not a raw parse error.
    assertThatThrownBy(() -> new AnalyticsExporterConfig().setSamplingRate("notanumber").validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("samplingRate must be between")
        .hasMessageContaining(String.valueOf(HashSampler.MIN_SAMPLE_RATE))
        .hasMessageContaining(String.valueOf(HashSampler.MAX_SAMPLE_RATE))
        .hasMessageContaining("notanumber");
  }

  @Test
  void shouldAcceptSamplingRateBoundaries() {
    assertThatCode(() -> new AnalyticsExporterConfig().setSamplingRate("0.0").validate())
        .doesNotThrowAnyException();
    assertThatCode(() -> new AnalyticsExporterConfig().setSamplingRate("1.0").validate())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldReturnSameDigestStringForEqualConfigs() {
    // given
    final var config = new AnalyticsExporterConfig().setSamplingRate("0.5");

    // when
    final var first = config.toExporterDigestString();
    final var second = config.toExporterDigestString();

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldReturnDifferentDigestStringWhenSamplingRateChanges() {
    // given
    final var configA = new AnalyticsExporterConfig().setSamplingRate("0.5");
    final var configB = new AnalyticsExporterConfig().setSamplingRate("0.25");

    // when / then
    assertThat(configA.toExporterDigestString()).isNotEqualTo(configB.toExporterDigestString());
  }

  @Test
  void shouldReturnSameDigestStringWhenNonBehaviorConfigChanges() {
    // given
    final var configA = new AnalyticsExporterConfig().setSamplingRate("1.0");
    final var configB =
        new AnalyticsExporterConfig()
            .setSamplingRate("1.0")
            .setEndpoint("https://other.example.com");

    // when / then
    assertThat(configA.toExporterDigestString()).isEqualTo(configB.toExporterDigestString());
  }
}
