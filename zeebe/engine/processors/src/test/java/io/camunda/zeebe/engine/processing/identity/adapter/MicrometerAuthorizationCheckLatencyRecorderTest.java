/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.core.port.out.AuthorizationCheckLatencyRecorder;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

final class MicrometerAuthorizationCheckLatencyRecorderTest {

  @Test
  void shouldRecordATimerMatchingTheSharedSpec() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var recorder = new MicrometerAuthorizationCheckLatencyRecorder(registry);

    // when
    recorder.record(TimeUnit.MILLISECONDS.toNanos(5));

    // then
    final var timer = registry.find(AuthorizationCheckLatencyRecorder.METRIC_NAME).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.getId().getDescription())
        .isEqualTo(AuthorizationCheckLatencyRecorder.METRIC_DESCRIPTION);
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(5.0);
  }

  @Test
  void shouldPublishTheSharedSloBucketsExactly() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var recorder = new MicrometerAuthorizationCheckLatencyRecorder(registry);

    // when
    recorder.record(1);

    // then — the SLO boundaries actually published must track the shared spec constant, since
    // the Grafana SLO-breach ratios are computed from these buckets
    final var timer = registry.find(AuthorizationCheckLatencyRecorder.METRIC_NAME).timer();
    final var actualBucketsNanos =
        Arrays.stream(timer.takeSnapshot().histogramCounts())
            .mapToDouble(CountAtBucket::bucket)
            .toArray();
    final var expectedBucketsNanos =
        AuthorizationCheckLatencyRecorder.METRIC_SLO_BUCKETS.stream()
            .mapToDouble(Duration::toNanos)
            .toArray();
    assertThat(actualBucketsNanos).containsExactly(expectedBucketsNanos, Offset.offset(1e-9));
  }
}
