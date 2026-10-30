/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoopJobMetricsStateTest {

  private MutableJobMetricsState state;

  @BeforeEach
  void setUp() {
    state = new NoopJobMetricsState();
  }

  @Test
  void shouldNotStoreMetricsWhenIncrementing() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);
    state.incrementMetric(jobRecord, JobMetricsExportState.COMPLETED);
    state.incrementMetric(jobRecord, JobMetricsExportState.FAILED);

    // then - forEach should not be called since no metrics are stored
    final AtomicInteger callCount = new AtomicInteger(0);
    state.forEach(
        (jobTypeIndex, tenantIdIndex, workerNameIndex, metrics) -> {
          callCount.incrementAndGet();
        });
    assertThat(callCount.get()).isZero();
  }

  @Test
  void shouldReturnEmptyEncodedStrings() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // when
    final var encodedStrings = state.getEncodedStrings();

    // then
    assertThat(encodedStrings).isEmpty();
  }

  @Test
  void shouldReturnZeroForAnyMetadataKey() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // when & then
    assertThat(state.getMetadata("anyKey")).isZero();
    assertThat(state.getMetadata("counter")).isZero();
    assertThat(state.getMetadata("size_limits_exceeded")).isZero();
  }

  @Test
  void shouldReturnFalseForIncompleteBatch() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // when & then
    assertThat(state.isIncompleteBatch()).isFalse();
  }

  @Test
  void shouldHandleCleanUpWithoutError() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // when & then - should not throw any exception
    state.cleanUp();
  }

  @Test
  void shouldHandleForEachWithoutCallingConsumer() {
    // given - no metrics stored due to noop

    // when
    final AtomicInteger callCount = new AtomicInteger(0);
    state.forEach(
        (jobTypeIndex, tenantIdIndex, workerNameIndex, metrics) -> {
          callCount.incrementAndGet();
        });

    // then
    assertThat(callCount.get()).isZero();
  }
}
