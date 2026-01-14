/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState.META_COUNTER;
import static io.camunda.zeebe.engine.state.jobmetrics.DbJobMetricsState.META_SIZE_LIMITS_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class DbJobMetricsExportStateTest {

  private MutableProcessingState processingState;
  private MutableJobMetricsState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getJobMetricsState();
  }

  @Test
  void shouldIncrementMetricForNewKey() {
    // given
    final String jobType = "testJobType";
    final String tenantId = "testTenant";
    final String workerName = "testWorker";

    // when
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(1);
  }

  @Test
  void shouldIncrementMetricForExistingKey() {
    // given
    final String jobType = "testJobType";
    final String tenantId = "testTenant";
    final String workerName = "testWorker";

    // when
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount())
        .isEqualTo(3);
  }

  @Test
  void shouldIncrementDifferentStatusesForSameKey() {
    // given
    final String jobType = "testJobType";
    final String tenantId = "testTenant";
    final String workerName = "testWorker";

    // when
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.COMPLETED);
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.FAILED);

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
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType1", "tenant2", "worker1", JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(3);
  }

  @Test
  void shouldEncodeStringsToIntegers() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when
    final Set<String> encodedStrings = state.getEncodedStrings();

    // then
    assertThat(encodedStrings).containsExactly("jobType1", "tenant1", "worker1");
  }

  @Test
  void shouldReuseEncodedStrings() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when
    final Set<String> encodedStrings = state.getEncodedStrings();

    // then - tenant1 and worker1 should be reused, only 4 unique strings
    assertThat(encodedStrings).containsExactly("jobType1", "tenant1", "worker1", "jobType2");
  }

  @Test
  void shouldUpdateLastUpdatedAtTimestamp() {
    // given
    final String jobType = "testJobType";
    final String tenantId = "testTenant";
    final String workerName = "testWorker";

    // when
    state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);

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
    state.incrementMetric("jobType", "tenant", "worker", JobMetricsExportState.CREATED);

    // then
    final long batchStartTime = state.getBatchStartTime();
    assertThat(batchStartTime).isGreaterThanOrEqualTo(beforeIncrement);
  }

  @Test
  void shouldUpdateBatchEndTimeOnEveryIncrement() {
    // given
    state.incrementMetric("jobType1", "tenant", "worker", JobMetricsExportState.CREATED);
    final long firstEndTime = state.getBatchEndTime();

    // when
    state.incrementMetric("jobType2", "tenant", "worker", JobMetricsExportState.CREATED);

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
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);
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
    state.incrementMetric("jobType", "tenant", "worker", JobMetricsExportState.CREATED);
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
  void shouldIncrementCounterForEachNewString() {
    // given / when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // then - 3 new strings created, counter should be 3
    assertThat(state.getMetadata(META_COUNTER)).isEqualTo(3L);
  }

  @Test
  void shouldNotIncrementCounterForReusedStrings() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    final long counterAfterFirst = state.getMetadata(META_COUNTER);

    // when - reuse all strings
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);

    // then
    assertThat(state.getMetadata(META_COUNTER)).isEqualTo(counterAfterFirst);
  }

  @Test
  void shouldIterateOverAllMetrics() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

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
    state.incrementMetric("jobType", "tenant1", "worker", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType", "tenant2", "worker", JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(2);
  }

  @Test
  void shouldTrackMetricsForSameJobTypeWithDifferentWorkers() {
    // given / when
    state.incrementMetric("jobType", "tenant", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType", "tenant", "worker2", JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(2);
  }

  @Test
  void shouldPreserveMetricCountsAcrossMultipleIncrements() {
    // given
    final String jobType = "testJobType";
    final String tenantId = "testTenant";
    final String workerName = "testWorker";

    // when
    for (int i = 0; i < 100; i++) {
      state.incrementMetric(jobType, tenantId, workerName, JobMetricsExportState.CREATED);
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
    state.incrementMetric("jobType", "tenant", "worker", JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(0);
    assertThat(metrics.getFirst()[JobMetricsExportState.FAILED.getIndex()].getCount()).isEqualTo(0);
  }

  @Test
  void shouldHandleEmptyStrings() {
    // given / when
    state.incrementMetric("", "", "", JobMetricsExportState.CREATED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(state.getEncodedStrings()).containsExactly("");
  }

  @Test
  void shouldMaintainEncodedStringsOrderByIndex() {
    // given
    state.incrementMetric("first", "second", "third", JobMetricsExportState.CREATED);
    state.incrementMetric("fourth", "second", "third", JobMetricsExportState.CREATED);

    // when
    final Set<String> encodedStrings = state.getEncodedStrings();

    // then - should be ordered by creation (index)
    assertThat(encodedStrings).containsExactly("first", "second", "third", "fourth");
  }

  @Test
  void shouldAllowAddingMetricsAfterCleanUp() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.cleanUp();

    // when
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

    // then
    final List<StatusMetrics[]> metrics = collectMetrics();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(1);
  }

  @Test
  void shouldResetStringEncodingCounterAfterCleanUp() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.cleanUp();

    // when
    state.incrementMetric("newJobType", "newTenant", "newWorker", JobMetricsExportState.CREATED);

    // then - strings should be re-encoded starting from 0
    final Set<String> encodedStrings = state.getEncodedStrings();
    assertThat(encodedStrings).containsExactly("newJobType", "newTenant", "newWorker");
  }

  private List<StatusMetrics[]> collectMetrics() {
    final List<StatusMetrics[]> result = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> result.add(metrics));
    return result;
  }
}
