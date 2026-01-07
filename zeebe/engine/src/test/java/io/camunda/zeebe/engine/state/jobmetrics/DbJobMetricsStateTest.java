/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class DbJobMetricsStateTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableJobMetricsState state;

  @BeforeEach
  void beforeEach() {
    state = new DbJobMetricsState(zeebeDb, transactionContext);
  }

  @Test
  void shouldIncrementMetricForNewKey() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);

    // then
    final List<int[]> keys = new ArrayList<>();
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach(
        (jobTypeIdx, tenantIdx, workerIdx, metrics) -> {
          keys.add(new int[] {jobTypeIdx, tenantIdx, workerIdx});
          values.add(metrics);
        });

    assertThat(keys).hasSize(1);
    assertThat(values).hasSize(1);
    assertThat(values.get(0)[JobState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(values.get(0)[JobState.CREATED.getIndex()].getLastUpdatedAt()).isGreaterThan(0);
  }

  @Test
  void shouldIncrementMetricForExistingKey() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);

    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.COMPLETED);

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    assertThat(values.get(0)[JobState.CREATED.getIndex()].getCount()).isEqualTo(2);
    assertThat(values.get(0)[JobState.COMPLETED.getIndex()].getCount()).isEqualTo(1);
  }

  @Test
  void shouldTrackDifferentKeysSeparately() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType2", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobState.COMPLETED);

    // then
    final List<int[]> keys = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> keys.add(new int[] {jobTypeIdx}));

    assertThat(keys).hasSize(3);
  }

  @Test
  void shouldEncodeStringsToIntegers() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobState.COMPLETED);

    // then
    final List<String> encodedStrings = state.getEncodedStrings();
    assertThat(encodedStrings).hasSize(6);
    assertThat(encodedStrings)
        .containsExactlyInAnyOrder(
            "jobType1", "tenant1", "worker1", "jobType2", "tenant2", "worker2");
  }

  @Test
  void shouldReuseEncodedStrings() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.COMPLETED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobState.FAILED);

    // then
    final List<String> encodedStrings = state.getEncodedStrings();
    // jobType1, tenant1, worker1, worker2 = 4 unique strings
    assertThat(encodedStrings).hasSize(4);
  }

  @Test
  void shouldUpdateMetadata() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobState.COMPLETED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    assertThat(jobMetricsNb).isEqualTo(2);

    final long counter = state.getMetadata(DbJobMetricsState.META_COUNTER);
    assertThat(counter).isEqualTo(6); // 6 unique strings

    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE);
    assertThat(totalEncodedStringsSize)
        .isEqualTo(
            "jobType1".length()
                + "tenant1".length()
                + "worker1".length()
                + "jobType2".length()
                + "tenant2".length()
                + "worker2".length());
  }

  @Test
  void shouldFlushAllData() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobState.COMPLETED);

    // when
    state.flush();

    // then
    final List<int[]> keys = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> keys.add(new int[] {jobTypeIdx}));
    assertThat(keys).isEmpty();

    final List<String> encodedStrings = state.getEncodedStrings();
    assertThat(encodedStrings).isEmpty();

    assertThat(state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_COUNTER)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE)).isZero();
  }

  @Test
  void shouldTrackAllJobStatuses() {
    // when
    for (final JobState status : JobState.values()) {
      state.incrementMetric("jobType1", "tenant1", "worker1", status);
    }

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    for (final JobState status : JobState.values()) {
      assertThat(values.get(0)[status.getIndex()].getCount()).isEqualTo(1);
    }
  }

  @Test
  void shouldCalculateBatchRecordTotalSize() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE);

    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long expectedSize =
        jobMetricsNb * (MetricsKey.BYTES + MetricsValue.BYTES) + totalEncodedStringsSize;
    assertThat(batchRecordTotalSize).isEqualTo(expectedSize);
  }

  @Test
  void shouldCalculateBatchRecordTotalSizeWithComplexChar() {

    // when
    state.incrementMetric("❌", "✅", "😅", JobState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE);

    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long expectedSize =
        jobMetricsNb * (MetricsKey.BYTES + MetricsValue.BYTES) + totalEncodedStringsSize;
    assertThat(batchRecordTotalSize).isEqualTo(expectedSize);
  }

  @Test
  void shouldReturnZeroForNonExistentMetadata() {
    // when/then
    assertThat(state.getMetadata("non_existent_key")).isZero();
  }
}
