/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl.META_SIZE_LIMITS_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link MutableJobMetricsState} implementation must share. Each backend runs these
 * tests by extending this class.
 */
public abstract class AbstractJobMetricsStateContractTest {

  private MutableJobMetricsState state;

  /** Creates the state under test with the default limits. Called before each test. */
  protected abstract MutableJobMetricsState createJobMetricsState();

  @BeforeEach
  void setUp() {
    state = createJobMetricsState();
  }

  @Test
  void shouldIncrementMetricForNewKey() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(1);
  }

  @Test
  void shouldIncrementMetricForExistingKey() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(3);
  }

  @Test
  void shouldIncrementDifferentStatusesForSameKey() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);
    state.incrementMetric(jobRecord, JobMetricsExportState.COMPLETED);
    state.incrementMetric(jobRecord, JobMetricsExportState.FAILED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.FAILED.getIndex()].getCount()).isEqualTo(1);
  }

  @Test
  void shouldTrackMultipleDifferentKeys() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant2").setWorker("worker1"),
        JobMetricsExportState.CREATED);

    // then
    final var encodedStrings = state.getEncodedStrings();
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(3);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(1);
    assertThat(metrics.get(1)[JobMetricsExportState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(metrics.get(2)[JobMetricsExportState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(encodedStrings)
        .containsExactlyInAnyOrder("jobType1", "tenant1", "worker1", "jobType2", "tenant2");
  }

  @Test
  void shouldEncodeStringsToIntegers() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);

    // when
    final List<String> encodedStrings = state.getEncodedStrings();

    // then
    assertThat(encodedStrings).containsExactly("jobType1", "tenant1", "worker1");
  }

  @Test
  void shouldReuseEncodedStrings() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);

    // when
    final List<String> encodedStrings = state.getEncodedStrings();

    // then - tenant1 and worker1 should be reused, only 4 unique strings
    assertThat(encodedStrings)
        .containsExactlyInAnyOrder("jobType1", "tenant1", "worker1", "jobType2");
  }

  @Test
  void shouldUpdateLastUpdatedAtTimestamp() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getLastUpdatedAt())
        .isGreaterThan(0);
  }

  @Test
  void shouldSetBatchStartTimeOnFirstIncrement() {
    // given
    final long beforeIncrement = System.currentTimeMillis();

    // when
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant").setWorker("worker"),
        JobMetricsExportState.CREATED);

    // then
    final long batchStartTime = state.getBatchStartTime();
    assertThat(batchStartTime).isGreaterThanOrEqualTo(beforeIncrement);
  }

  @Test
  void shouldUpdateBatchEndTimeOnEveryIncrement() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant").setWorker("worker"),
        JobMetricsExportState.CREATED);
    final long firstEndTime = state.getBatchEndTime();

    // when
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant").setWorker("worker"),
        JobMetricsExportState.CREATED);

    // then
    final long secondEndTime = state.getBatchEndTime();
    assertThat(secondEndTime).isGreaterThanOrEqualTo(firstEndTime);
  }

  @Test
  void shouldNotBeIncompleteBatchInitially() {
    // when / then
    assertThat(state.isIncompleteBatch()).isFalse();
  }

  @Test
  void shouldCleanUpAllData() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant2").setWorker("worker2"),
        JobMetricsExportState.COMPLETED);
    assertThat(collectMetrics()).hasSize(2);

    // when
    state.cleanUp();

    // then
    assertThat(collectMetrics()).isEmpty();
    assertThat(state.getEncodedStrings()).isEmpty();
    assertThat(state.isIncompleteBatch()).isFalse();
    assertThat(state.getMetadata(META_SIZE_LIMITS_EXCEEDED)).isEqualTo(0L);
  }

  @Test
  void shouldResetMetadataAfterCleanUp() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant").setWorker("worker"),
        JobMetricsExportState.CREATED);
    assertThat(state.getBatchStartTime()).isGreaterThan(0);

    // when
    state.cleanUp();

    // then
    assertThat(state.getBatchStartTime()).isEqualTo(-1L);
  }

  @Test
  void shouldReturnZeroForUnsetMetadata() {
    // when / then
    assertThat(state.getMetadata("nonExistentKey")).isEqualTo(0L);
  }

  @Test
  void shouldIterateOverAllMetrics() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant2").setWorker("worker2"),
        JobMetricsExportState.COMPLETED);

    // when
    final List<int[]> indices = new ArrayList<>();
    state.forEach(
        (jobTypeIdx, tenantIdx, workerIdx, metrics) ->
            indices.add(new int[] {jobTypeIdx, tenantIdx, workerIdx}));

    // then
    assertThat(indices).hasSize(2);
  }

  @Test
  void shouldTrackMetricsForSameJobTypeWithDifferentTenants() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant1").setWorker("worker"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant2").setWorker("worker"),
        JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(2);
  }

  @Test
  void shouldTrackMetricsForSameJobTypeWithDifferentWorkers() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant").setWorker("worker2"),
        JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(2);
  }

  @Test
  void shouldPreserveMetricCountsAcrossMultipleIncrements() {
    // given
    final var jobRecord =
        new JobRecord().setType("testJobType").setTenantId("testTenant").setWorker("testWorker");

    // when
    for (int i = 0; i < 100; i++) {
      state.incrementMetric(jobRecord, JobMetricsExportState.CREATED);
    }

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(100);
  }

  @Test
  void shouldKeepZeroCountsForUnusedStatuses() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("jobType").setTenantId("tenant").setWorker("worker"),
        JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(0);
    assertThat(metrics.getFirst()[JobMetricsExportState.FAILED.getIndex()].getCount()).isEqualTo(0);
  }

  @Test
  void shouldHandleEmptyStrings() {
    // given / when
    state.incrementMetric(
        new JobRecord().setType("").setTenantId("").setWorker(""), JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(state.getEncodedStrings()).containsExactly("");
  }

  @Test
  void shouldEncodeEveryMetricWithTheIndicesOfItsStrings() {
    // given
    state.incrementMetric(
        new JobRecord().setType("first").setTenantId("second").setWorker("third"),
        JobMetricsExportState.CREATED);
    state.incrementMetric(
        new JobRecord().setType("fourth").setTenantId("second").setWorker("third"),
        JobMetricsExportState.COMPLETED);
    state.incrementMetric(
        new JobRecord().setType("fourth").setTenantId("second").setWorker("third"),
        JobMetricsExportState.COMPLETED);

    // when
    final List<String> encodedStrings = state.getEncodedStrings();
    final Map<String, Integer> completedByKey = new HashMap<>();
    state.forEach(
        (jobTypeIdx, tenantIdx, workerIdx, metrics) ->
            completedByKey.put(
                String.join(
                    "/",
                    encodedStrings.get(jobTypeIdx),
                    encodedStrings.get(tenantIdx),
                    encodedStrings.get(workerIdx)),
                metrics[JobMetricsExportState.COMPLETED.getIndex()].getCount()));

    // then
    assertThat(encodedStrings).containsExactlyInAnyOrder("first", "second", "third", "fourth");
    assertThat(completedByKey)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("first/second/third", 0, "fourth/second/third", 2));
  }

  @Test
  void shouldAllowAddingMetricsAfterCleanUp() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.cleanUp();

    // when
    state.incrementMetric(
        new JobRecord().setType("jobType2").setTenantId("tenant2").setWorker("worker2"),
        JobMetricsExportState.COMPLETED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(1);
  }

  @Test
  void shouldResetStringEncodingCounterAfterCleanUp() {
    // given
    state.incrementMetric(
        new JobRecord().setType("jobType1").setTenantId("tenant1").setWorker("worker1"),
        JobMetricsExportState.CREATED);
    state.cleanUp();

    // when
    state.incrementMetric(
        new JobRecord().setType("newJobType").setTenantId("newTenant").setWorker("newWorker"),
        JobMetricsExportState.CREATED);

    // then - strings should be re-encoded starting from 0
    final List<String> encodedStrings = state.getEncodedStrings();
    assertThat(encodedStrings).containsExactly("newJobType", "newTenant", "newWorker");
  }

  private List<StatusMetrics[]> collectMetrics() {
    final List<StatusMetrics[]> result = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> result.add(metrics));
    return result;
  }
}
