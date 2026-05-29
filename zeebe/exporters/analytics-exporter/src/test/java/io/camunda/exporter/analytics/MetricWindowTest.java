/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MAX;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MIN;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_END;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_START;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_SEQUENCE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricWindowTest {

  @Test
  void shouldTrackSingleEvent() {
    // given
    final var window = new MetricWindow();

    // when
    window.record(100L, 5000L);

    // then
    assertThat(window.hasEvents()).isTrue();
    assertThat(window.eventCount()).isEqualTo(1);
    final var attrs = window.toGaugeAttributes(42L);
    assertThat(attrs.get(METRIC_SEQUENCE_NUMBER)).isEqualTo(42L);
    // event_count is the gauge value, not an attribute
    assertThat(attrs.get(LOG_POSITION_START)).isEqualTo(100L);
    assertThat(attrs.get(LOG_POSITION_END)).isEqualTo(100L);
    assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isEqualTo(5000L);
    assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isEqualTo(5000L);
  }

  @Test
  void shouldTrackMinMaxAcrossMultipleEvents() {
    // given
    final var window = new MetricWindow();

    // when
    window.record(200L, 6000L);
    window.record(100L, 5000L);
    window.record(300L, 7000L);

    // then
    assertThat(window.eventCount()).isEqualTo(3);
    final var attrs = window.toGaugeAttributes(1L);
    assertThat(attrs.get(LOG_POSITION_START)).isEqualTo(100L);
    assertThat(attrs.get(LOG_POSITION_END)).isEqualTo(300L);
    assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isEqualTo(5000L);
    assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isEqualTo(7000L);
  }

  @Test
  void shouldResetToEmptyState() {
    // given
    final var window = new MetricWindow();
    window.record(100L, 5000L);

    // when
    window.reset();

    // then
    assertThat(window.hasEvents()).isFalse();
    assertThat(window.eventCount()).isZero();
  }

  @Test
  void shouldTrackCorrectlyAfterReset() {
    // given
    final var window = new MetricWindow();
    window.record(100L, 5000L);
    window.reset();

    // when
    window.record(500L, 9000L);

    // then
    assertThat(window.eventCount()).isEqualTo(1);
    final var attrs = window.toGaugeAttributes(2L);
    assertThat(attrs.get(LOG_POSITION_START)).isEqualTo(500L);
    assertThat(attrs.get(LOG_POSITION_END)).isEqualTo(500L);
    assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isEqualTo(9000L);
    assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isEqualTo(9000L);
  }

  @Test
  void shouldReportEmptyBeforeAnyRecords() {
    // given
    final var window = new MetricWindow();

    // then
    assertThat(window.hasEvents()).isFalse();
    assertThat(window.eventCount()).isZero();
  }
}
