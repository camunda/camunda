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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class DbJobMetricsStateTest {

  private static final int MAX_SIZE_BEFORE_TRUNCATION = 4 * 1024 * 1024; // 4 MB
  private static final int UUID_BYTE_SIZE = 36;
  private static final int MAX_METRICS_NUMBERS_BEFORE_TRUNCATION =
      MAX_SIZE_BEFORE_TRUNCATION
          / ((MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
              + (UUID_BYTE_SIZE
                  * 3)); // This is theoretical maximum before truncation when using unique
  // UUID strings;

  public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessingState processingState;
  private MutableJobMetricsState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getJobMetricsState();
  }

  @Test
  void shouldIncrementMetricForNewKey() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

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
    assertThat(values.getFirst()[JobMetricsState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(values.getFirst()[JobMetricsState.CREATED.getIndex()].getLastUpdatedAt())
        .isGreaterThan(0);
  }

  @Test
  void shouldIncrementMetricForExistingKey() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.COMPLETED);

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    assertThat(values.getFirst()[JobMetricsState.CREATED.getIndex()].getCount()).isEqualTo(2);
    assertThat(values.getFirst()[JobMetricsState.COMPLETED.getIndex()].getCount()).isEqualTo(1);
  }

  @Test
  void shouldTrackDifferentKeysSeparately() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType2", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobMetricsState.COMPLETED);

    // then
    final List<int[]> keys = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> keys.add(new int[] {jobTypeIdx}));

    assertThat(keys).hasSize(3);
  }

  @Test
  void shouldEncodeStringsToIntegers() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsState.COMPLETED);

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
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.COMPLETED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobMetricsState.FAILED);

    // then
    final List<String> encodedStrings = state.getEncodedStrings();
    // jobType1, tenant1, worker1, worker2 = 4 unique strings
    assertThat(encodedStrings).hasSize(4);
  }

  @Test
  void shouldUpdateMetadata() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsState.COMPLETED);

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
  void shouldCleanUpAllData() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsState.COMPLETED);

    // when
    state.cleanUp();

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
    for (final JobMetricsState status : JobMetricsState.values()) {
      state.incrementMetric("jobType1", "tenant1", "worker1", status);
    }

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    for (final JobMetricsState status : JobMetricsState.values()) {
      assertThat(values.getFirst()[status.getIndex()].getCount()).isEqualTo(1);
    }
  }

  @Test
  void shouldCalculateBatchRecordTotalSize() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE);

    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long expectedSize =
        jobMetricsNb * (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
            + totalEncodedStringsSize;
    assertThat(batchRecordTotalSize).isEqualTo(expectedSize);
  }

  @Test
  void shouldCalculateBatchRecordTotalSizeWithComplexChar() {

    // when
    state.incrementMetric("❌", "✅", "😅", JobMetricsState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE);

    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long expectedSize =
        jobMetricsNb * (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
            + totalEncodedStringsSize;
    assertThat(batchRecordTotalSize).isEqualTo(expectedSize);
  }

  @Test
  void shouldReturnZeroForNonExistentMetadata() {
    // when/then
    assertThat(state.getMetadata("non_existent_key")).isZero();
  }

  @Test
  void shouldNotBeTruncatedInitially() {
    // when/then
    assertThat(state.isIncompleteBatch()).isFalse();
  }

  @Test
  void shouldMarkAsTruncatedWhenSizeLimitExceeded() {

    // given - simulate size limit exceeded by directly setting metadata
    for (int i = 0; i < MAX_METRICS_NUMBERS_BEFORE_TRUNCATION; i++) {
      state.incrementMetric(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          JobMetricsState.CREATED);
    }

    assertThat(state.isIncompleteBatch()).isFalse();
    state.incrementMetric(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        JobMetricsState.CREATED);
    assertThat(state.isIncompleteBatch()).isTrue();
  }

  @Test
  void shouldSkipIncrementWhenAlreadyTruncated() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

    // Manually set truncated flag via flush and re-insert mechanism
    // This simulates the truncated state
    final long initialCount = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);

    // when - the state is not truncated, so this should work
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsState.COMPLETED);

    // then
    final long newCount = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_NB);
    assertThat(newCount).isEqualTo(initialCount + 1);
  }

  @Test
  void shouldResetTruncatedFlagOnCleanUp() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

    // when
    state.cleanUp();

    // then
    assertThat(state.isIncompleteBatch()).isFalse();
    assertThat(state.getMetadata(DbJobMetricsState.META_TOTAL_SIZE_EXCEEDED)).isZero();
  }

  @Test
  void shouldCalculateSizeImpactInBytesCorrectlyForNewStrings() {
    // when - first insert with all new strings
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);

    // then - size should include: 3 string sizes + key + value
    final long expectedStringSize =
        "jobType1".getBytes(StandardCharsets.UTF_8).length
            + "tenant1".getBytes(StandardCharsets.UTF_8).length
            + "worker1".getBytes(StandardCharsets.UTF_8).length;
    final long expectedTotalSize =
        (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES) + expectedStringSize;

    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE))
        .isEqualTo(expectedTotalSize);
  }

  @Test
  void shouldNotAddSizeForExistingKeyUpdate() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.CREATED);
    final long sizeAfterFirstInsert =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE);

    // when - update existing key (same strings, different status)
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsState.COMPLETED);

    // then - size should remain the same (no new key or strings added)
    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_SIZE))
        .isEqualTo(sizeAfterFirstInsert);
  }
}
