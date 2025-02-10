/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.engine.impl.ScheduledCommandCacheMetrics.BoundedCommandCacheMetrics;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

final class BoundedCommandCacheMetricsTest {
  @Test
  void shouldReportSizeForIntent() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var metrics = new BoundedCommandCacheMetrics(registry);
    final IntConsumer timeout = metrics.forIntent(JobIntent.TIME_OUT);
    final IntConsumer recur = metrics.forIntent(JobIntent.RECUR_AFTER_BACKOFF);

    // when
    timeout.accept(10);
    recur.accept(20);
    timeout.accept(30);

    // then
    final var timeoutGauge =
        registry
            .get("zeebe.stream.processor.scheduled.command.cache.size")
            .tag("intent", "JobIntent.TIME_OUT")
            .gauge();
    final var recurGauge =
        registry
            .get("zeebe.stream.processor.scheduled.command.cache.size")
            .tag("intent", "JobIntent.RECUR_AFTER_BACKOFF")
            .gauge();
    assertThat(timeoutGauge).returns(30.0, Gauge::value);
    assertThat(recurGauge).returns(20.0, Gauge::value);
  }
}
