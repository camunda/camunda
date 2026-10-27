/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public abstract class ArchiverJobRecordingMetricsAbstractTest {

  abstract ArchiverJob<?> getArchiverJob();

  abstract SimpleMeterRegistry getMeterRegistry();

  abstract String getJobMetricName();

  @Test
  void shouldRecordMetrics() {
    // when
    final int count = getArchiverJob().execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3);
    assertArchivingCounts(count); // asserted as 3 above
    assertArchiverTimer(1); // job executed once
  }

  @Test
  void shouldMetricCountsIncreaseWithMultipleRuns() {
    // when
    final var count =
        getArchiverJob().execute().toCompletableFuture().join()
            + getArchiverJob().execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(6);
    assertArchivingCounts(count); // asserted as 6 above
    assertArchiverTimer(2); // job executed twice
  }

  void assertArchivingCounts(final int expected) {
    assertThat(getMeterRegistry().counter(getJobMetricName(), "state", "archiving").count())
        .isEqualTo(expected);
    assertThat(getMeterRegistry().counter(getJobMetricName(), "state", "archived").count())
        .isEqualTo(expected);
  }

  void assertArchiverTimer(final int expectedInvocations) {
    final Timer archiverTimer =
        getMeterRegistry().timer("zeebe.camunda.exporter.archiver.duration");
    assertThat(archiverTimer.count()).isEqualTo(expectedInvocations);
    assertThat(archiverTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }
}
