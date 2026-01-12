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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.ByteValue;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
class DbJobMetricsExportStateTest {

  private static final long MAX_SIZE_BEFORE_TRUNCATION = ByteValue.ofMegabytes(4); // 4 MB
  private static final long UUID_BYTE_SIZE = 36;
  private static final long MAX_METRICS_NUMBERS_BEFORE_TRUNCATION =
      MAX_SIZE_BEFORE_TRUNCATION
          / ((MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
              + (UUID_BYTE_SIZE
                  * 3)); // This is theoretical maximum before truncation when using unique
  // UUID strings;

  public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableProcessingState processingState;
  private MutableJobMetricsState state;

  @BeforeEach
  public void setUp() {
    state = processingState.getJobMetricsState();
  }

  @Test
  void shouldIncrementMetricForNewKey() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

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
    assertThat(values.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(values.getFirst()[JobMetricsExportState.CREATED.getIndex()].getLastUpdatedAt())
        .isGreaterThan(0);
  }

  @Test
  void shouldIncrementMetricForExistingKey() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    assertThat(values.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount()).isEqualTo(2);
    assertThat(values.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(1);
  }

  @Test
  void shouldTrackDifferentKeysSeparately() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobMetricsExportState.COMPLETED);

    // then
    final List<int[]> keys = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> keys.add(new int[] {jobTypeIdx}));

    assertThat(keys).hasSize(3);
  }

  @Test
  void shouldEncodeStringsToIntegers() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

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
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);
    state.incrementMetric("jobType1", "tenant1", "worker2", JobMetricsExportState.FAILED);

    // then
    final List<String> encodedStrings = state.getEncodedStrings();
    // jobType1, tenant1, worker1, worker2 = 4 unique strings
    assertThat(encodedStrings).hasSize(4);
  }

  @Test
  void shouldUpdateMetadata() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);
    assertThat(jobMetricsNb).isEqualTo(2);

    final long counter = state.getMetadata(DbJobMetricsState.META_COUNTER);
    assertThat(counter).isEqualTo(6); // 6 unique strings

    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE);
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
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

    // when
    state.cleanUp();

    // then
    final List<int[]> keys = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> keys.add(new int[] {jobTypeIdx}));
    assertThat(keys).isEmpty();

    final List<String> encodedStrings = state.getEncodedStrings();
    assertThat(encodedStrings).isEmpty();

    assertThat(state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_COUNTER)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE)).isZero();
    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE)).isZero();
  }

  @Test
  void shouldTrackAllJobStatuses() {
    // when
    for (final JobMetricsExportState status : JobMetricsExportState.values()) {
      state.incrementMetric("jobType1", "tenant1", "worker1", status);
    }

    // then
    final List<StatusMetrics[]> values = new ArrayList<>();
    state.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    for (final JobMetricsExportState status : JobMetricsExportState.values()) {
      assertThat(values.getFirst()[status.getIndex()].getCount()).isEqualTo(1);
    }
  }

  @Test
  void shouldCalculateBatchRecordTotalSize() {
    // when
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);

    // Formula: job_metrics_nb * (12 + 96) + total_encoded_strings_size
    final long expectedSize =
        jobMetricsNb * (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES)
            + totalEncodedStringsSize;
    assertThat(batchRecordTotalSize).isEqualTo(expectedSize);
  }

  @Test
  void shouldCalculateBatchRecordTotalSizeWithComplexChar() {

    // when
    state.incrementMetric("❌", "✅", "😅", JobMetricsExportState.CREATED);

    // then
    final long jobMetricsNb = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);
    final long totalEncodedStringsSize =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE);
    final long batchRecordTotalSize =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);

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
          JobMetricsExportState.CREATED);
    }

    assertThat(state.isIncompleteBatch()).isFalse();
    state.incrementMetric(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        JobMetricsExportState.CREATED);
    assertThat(state.isIncompleteBatch()).isTrue();
  }

  @Test
  void shouldSkipIncrementWhenAlreadyTruncated() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // Manually set truncated flag via flush and re-insert mechanism
    // This simulates the truncated state
    final long initialCount = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);

    // when - the state is not truncated, so this should work
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.COMPLETED);

    // then
    final long newCount = state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);
    assertThat(newCount).isEqualTo(initialCount + 1);
  }

  @Test
  void shouldResetTruncatedFlagOnCleanUp() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when
    state.cleanUp();

    // then
    assertThat(state.isIncompleteBatch()).isFalse();
    assertThat(state.getMetadata(DbJobMetricsState.META_TOTAL_SIZE_EXCEEDED)).isZero();
  }

  @Test
  void shouldCalculateSizeImpactInBytesInBytesCorrectlyForNewStrings() {
    // when - first insert with all new strings
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // then - size should include: 3 string sizes + key + value
    final long expectedStringSize =
        "jobType1".getBytes(StandardCharsets.UTF_8).length
            + "tenant1".getBytes(StandardCharsets.UTF_8).length
            + "worker1".getBytes(StandardCharsets.UTF_8).length;
    final long expectedTotalSize =
        (MetricsKey.TOTAL_SIZE_BYTES + MetricsValue.TOTAL_SIZE_BYTES) + expectedStringSize;

    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE))
        .isEqualTo(expectedTotalSize);
  }

  @Test
  void shouldNotAddSizeForExistingKeyUpdate() {
    // given
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    final long sizeAfterFirstInsert =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);

    // when - update existing key (same strings, different status)
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);

    // then - size should remain the same (no new key or strings added)
    assertThat(state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE))
        .isEqualTo(sizeAfterFirstInsert);
  }

  @Test
  void shouldPopulateCachesOnRestart() {
    // given - insert data before "restart"
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);
    state.incrementMetric("jobType2", "tenant2", "worker2", JobMetricsExportState.FAILED);

    final long jobMetricsNbBeforeRestart =
        state.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER);
    final long counterBeforeRestart = state.getMetadata(DbJobMetricsState.META_COUNTER);
    final long totalEncodedStringsSizeBeforeRestart =
        state.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE);
    final long batchRecordTotalSizeBeforeRestart =
        state.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);

    // Collect metrics data before restart
    final List<int[]> keysBeforeRestart = new ArrayList<>();
    final List<int[]> metricsBeforeRestart = new ArrayList<>();
    state.forEach(
        (jobTypeIdx, tenantIdx, workerIdx, metrics) -> {
          keysBeforeRestart.add(new int[] {jobTypeIdx, tenantIdx, workerIdx});
          metricsBeforeRestart.add(
              new int[] {
                metrics[JobMetricsExportState.CREATED.getIndex()].getCount(),
                metrics[JobMetricsExportState.COMPLETED.getIndex()].getCount(),
                metrics[JobMetricsExportState.FAILED.getIndex()].getCount()
              });
        });

    // when - simulate restart by creating a new DbJobMetricsState instance
    final MutableJobMetricsState restartedState =
        new DbJobMetricsState(zeebeDb, transactionContext, InstantSource.system());

    // then - verify metadata is correctly loaded
    assertThat(restartedState.getMetadata(DbJobMetricsState.META_JOB_METRICS_KEYS_NUMBER))
        .isEqualTo(jobMetricsNbBeforeRestart);
    assertThat(restartedState.getMetadata(DbJobMetricsState.META_COUNTER))
        .isEqualTo(counterBeforeRestart);
    assertThat(restartedState.getMetadata(DbJobMetricsState.META_TOTAL_ENCODED_STRINGS_BYTE_SIZE))
        .isEqualTo(totalEncodedStringsSizeBeforeRestart);
    assertThat(restartedState.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE))
        .isEqualTo(batchRecordTotalSizeBeforeRestart);

    // Verify metrics data is correctly loaded into cache
    final List<int[]> keysAfterRestart = new ArrayList<>();
    final List<int[]> metricsAfterRestart = new ArrayList<>();
    restartedState.forEach(
        (jobTypeIdx, tenantIdx, workerIdx, metrics) -> {
          keysAfterRestart.add(new int[] {jobTypeIdx, tenantIdx, workerIdx});
          metricsAfterRestart.add(
              new int[] {
                metrics[JobMetricsExportState.CREATED.getIndex()].getCount(),
                metrics[JobMetricsExportState.COMPLETED.getIndex()].getCount(),
                metrics[JobMetricsExportState.FAILED.getIndex()].getCount()
              });
        });

    assertThat(keysAfterRestart).hasSameSizeAs(keysBeforeRestart);
    for (int i = 0; i < keysBeforeRestart.size(); i++) {
      assertThat(keysAfterRestart.get(i)).containsExactly(keysBeforeRestart.get(i));
      assertThat(metricsAfterRestart.get(i)).containsExactly(metricsBeforeRestart.get(i));
    }

    // Verify string encoding cache is populated
    final List<String> encodedStrings = restartedState.getEncodedStrings();
    assertThat(encodedStrings).hasSize(6); // jobType1, tenant1, worker1, jobType2, tenant2, worker2
  }

  @Test
  void shouldContinueIncrementingAfterRestart() {
    // given - insert data before "restart"
    state.incrementMetric("jobType1", "tenant1", "worker1", JobMetricsExportState.CREATED);

    // when - simulate restart
    final MutableJobMetricsState restartedState =
        new DbJobMetricsState(zeebeDb, transactionContext, InstantSource.system());

    // then - should be able to continue incrementing existing key
    restartedState.incrementMetric(
        "jobType1", "tenant1", "worker1", JobMetricsExportState.COMPLETED);

    final List<StatusMetrics[]> values = new ArrayList<>();
    restartedState.forEach((jobTypeIdx, tenantIdx, workerIdx, metrics) -> values.add(metrics));

    assertThat(values).hasSize(1);
    assertThat(values.getFirst()[JobMetricsExportState.CREATED.getIndex()].getCount()).isEqualTo(1);
    assertThat(values.getFirst()[JobMetricsExportState.COMPLETED.getIndex()].getCount())
        .isEqualTo(1);

    // and - should be able to add new keys using existing strings (no size increase for strings)
    final long sizeBeforeNewKey =
        restartedState.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);
    restartedState.incrementMetric("jobType1", "tenant1", "worker2", JobMetricsExportState.CREATED);
    final long sizeAfterNewKey =
        restartedState.getMetadata(DbJobMetricsState.META_BATCH_RECORD_TOTAL_BYTES_SIZE);

    // Size increase should only be for new string "worker2" + new key/value
    final long expectedIncrease =
        "worker2".getBytes(StandardCharsets.UTF_8).length
            + MetricsKey.TOTAL_SIZE_BYTES
            + MetricsValue.TOTAL_SIZE_BYTES;
    assertThat(sizeAfterNewKey - sizeBeforeNewKey).isEqualTo(expectedIncrease);
  }
}
