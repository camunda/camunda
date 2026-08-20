/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.service.JobMetricsBatchWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableStatusMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobMetricsBatchExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private JobMetricsBatchWriter jobMetricsBatchWriter;

  @Captor private ArgumentCaptor<JobMetricsBatchDbModel> dbModelCaptor;

  private JobMetricsBatchExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new JobMetricsBatchExportHandler(jobMetricsBatchWriter);
  }

  @Test
  @DisplayName("Should be able to export record with EXPORTED intent")
  void shouldExportRecordWithExportedIntent() {
    // given
    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH, r -> r.withIntent(JobMetricsBatchIntent.EXPORTED));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should be able to export record with EXPORTED intent")
        .isTrue();
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @EnumSource(
      value = JobMetricsBatchIntent.class,
      names = {"EXPORTED"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldNotExportRecordWithUnsupportedIntent(final JobMetricsBatchIntent intent) {
    // given
    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(ValueType.JOB_METRICS_BATCH, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should not be able to export record with unsupported intent: %s", intent)
        .isFalse();
  }

  @Test
  @DisplayName("Should export single job metrics batch record")
  void shouldExportSingleJobMetricsBatchRecord() {
    // given
    final long startTime = 1000L;
    final long endTime = 2000L;
    final long lastCreatedAt = 1100L;
    final long lastCompletedAt = 1200L;
    final long lastFailedAt = 1300L;

    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(startTime)
            .withBatchEndTime(endTime)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(
                            List.of(
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(5)
                                    .withLastUpdatedAt(lastCreatedAt)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(10)
                                    .withLastUpdatedAt(lastCompletedAt)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(3)
                                    .withLastUpdatedAt(lastFailedAt)
                                    .build()))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(123L)
                    .withPartitionId(1)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then
    verify(jobMetricsBatchWriter, times(1)).create(dbModelCaptor.capture());

    final JobMetricsBatchDbModel model = dbModelCaptor.getValue();
    assertThat(model.key()).isEqualTo("123_0_1_2");
    assertThat(model.partitionId()).isEqualTo(1);
    assertThat(model.startTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC));
    assertThat(model.endTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC));
    assertThat(model.incompleteBatch()).isFalse();
    assertThat(model.tenantId()).isEqualTo("tenant1");
    assertThat(model.jobType()).isEqualTo("jobType1");
    assertThat(model.worker()).isEqualTo("worker1");
    assertThat(model.createdCount()).isEqualTo(5);
    assertThat(model.lastCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastCreatedAt), ZoneOffset.UTC));
    assertThat(model.completedCount()).isEqualTo(10);
    assertThat(model.lastCompletedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastCompletedAt), ZoneOffset.UTC));
    assertThat(model.failedCount()).isEqualTo(3);
    assertThat(model.lastFailedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastFailedAt), ZoneOffset.UTC));

    verifyNoMoreInteractions(jobMetricsBatchWriter);
  }

  @Test
  @DisplayName("Should export multiple job metrics from same batch record")
  void shouldExportMultipleJobMetricsFromSameBatch() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(true)
            .withEncodedStrings(
                List.of("jobType1", "tenant1", "worker1", "jobType2", "tenant2", "worker2"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics(5, 10, 3))
                        .build(),
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(3)
                        .withTenantIdIndex(4)
                        .withWorkerNameIndex(5)
                        .withStatusMetrics(createStatusMetrics(7, 15, 2))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(456L)
                    .withPartitionId(2)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then
    verify(jobMetricsBatchWriter, times(2)).create(dbModelCaptor.capture());

    final List<JobMetricsBatchDbModel> models = dbModelCaptor.getAllValues();
    assertThat(models).hasSize(2);

    // First metric
    final JobMetricsBatchDbModel model1 = models.get(0);
    assertThat(model1.key()).isEqualTo("456_0_1_2");
    assertThat(model1.partitionId()).isEqualTo(2);
    assertThat(model1.tenantId()).isEqualTo("tenant1");
    assertThat(model1.jobType()).isEqualTo("jobType1");
    assertThat(model1.worker()).isEqualTo("worker1");
    assertThat(model1.createdCount()).isEqualTo(5);
    assertThat(model1.completedCount()).isEqualTo(10);
    assertThat(model1.failedCount()).isEqualTo(3);
    assertThat(model1.incompleteBatch()).isTrue();

    // Second metric
    final JobMetricsBatchDbModel model2 = models.get(1);
    assertThat(model2.key()).isEqualTo("456_3_4_5");
    assertThat(model2.partitionId()).isEqualTo(2);
    assertThat(model2.tenantId()).isEqualTo("tenant2");
    assertThat(model2.jobType()).isEqualTo("jobType2");
    assertThat(model2.worker()).isEqualTo("worker2");
    assertThat(model2.createdCount()).isEqualTo(7);
    assertThat(model2.completedCount()).isEqualTo(15);
    assertThat(model2.failedCount()).isEqualTo(2);
    assertThat(model2.incompleteBatch()).isTrue();

    verifyNoMoreInteractions(jobMetricsBatchWriter);
  }

  @Test
  @DisplayName("Should correctly map partition ID from record")
  void shouldCorrectlyMapPartitionIdFromRecord() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics(1, 2, 3))
                        .build()))
            .build();

    // Test with partition 0
    final Record<JobMetricsBatchRecordValue> recordPartition0 =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(100L)
                    .withPartitionId(0)
                    .withValue(jobMetricsBatchRecordValue));

    // Test with partition 5
    final Record<JobMetricsBatchRecordValue> recordPartition5 =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(200L)
                    .withPartitionId(5)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(recordPartition0);
    handler.export(recordPartition5);

    // then
    verify(jobMetricsBatchWriter, times(2)).create(dbModelCaptor.capture());

    final List<JobMetricsBatchDbModel> models = dbModelCaptor.getAllValues();
    assertThat(models.get(0).partitionId())
        .as("First record should have partition ID 0")
        .isEqualTo(0);
    assertThat(models.get(1).partitionId())
        .as("Second record should have partition ID 5")
        .isEqualTo(5);

    verifyNoMoreInteractions(jobMetricsBatchWriter);
  }

  @Test
  @DisplayName("Should correctly generate composite key from record key and metric indices")
  void shouldCorrectlyGenerateCompositeKey() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics(1, 2, 3))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(999L)
                    .withPartitionId(1)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then
    verify(jobMetricsBatchWriter).create(dbModelCaptor.capture());

    final JobMetricsBatchDbModel model = dbModelCaptor.getValue();
    assertThat(model.key())
        .as(
            "Key should be composite of record key and metric indices: recordKey_jobTypeIndex_tenantIdIndex_workerNameIndex")
        .isEqualTo("999_0_1_2");
  }

  @Test
  @DisplayName("Should handle record with incomplete batch flag set to true")
  void shouldHandleRecordWithIncompleteBatchFlag() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(true) // incomplete batch
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics(1, 2, 3))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(123L)
                    .withPartitionId(1)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then
    verify(jobMetricsBatchWriter).create(dbModelCaptor.capture());

    final JobMetricsBatchDbModel model = dbModelCaptor.getValue();
    assertThat(model.incompleteBatch())
        .as("Incomplete batch flag should be true when record size limit exceeded")
        .isTrue();
  }

  @Test
  @DisplayName("Should correctly map all status metrics (CREATED, COMPLETED, FAILED)")
  void shouldCorrectlyMapAllStatusMetrics() {
    // given
    final long createdAt = 1100L;
    final long completedAt = 1200L;
    final long failedAt = 1300L;

    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(
                            List.of(
                                // CREATED (index 0)
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(100)
                                    .withLastUpdatedAt(createdAt)
                                    .build(),
                                // COMPLETED (index 1)
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(200)
                                    .withLastUpdatedAt(completedAt)
                                    .build(),
                                // FAILED (index 2)
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(300)
                                    .withLastUpdatedAt(failedAt)
                                    .build()))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(123L)
                    .withPartitionId(1)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then
    verify(jobMetricsBatchWriter).create(dbModelCaptor.capture());

    final JobMetricsBatchDbModel model = dbModelCaptor.getValue();

    // CREATED metrics
    assertThat(model.createdCount())
        .as("Created count should match CREATED status metric")
        .isEqualTo(100);
    assertThat(model.lastCreatedAt())
        .as("Last created at should match CREATED status metric timestamp")
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneOffset.UTC));

    // COMPLETED metrics
    assertThat(model.completedCount())
        .as("Completed count should match COMPLETED status metric")
        .isEqualTo(200);
    assertThat(model.lastCompletedAt())
        .as("Last completed at should match COMPLETED status metric timestamp")
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(completedAt), ZoneOffset.UTC));

    // FAILED metrics
    assertThat(model.failedCount())
        .as("Failed count should match FAILED status metric")
        .isEqualTo(300);
    assertThat(model.lastFailedAt())
        .as("Last failed at should match FAILED status metric timestamp")
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(failedAt), ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Should handle empty job metrics list gracefully")
  void shouldHandleEmptyJobMetricsList() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of())
            .withJobMetrics(List.of()) // empty metrics
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(123L)
                    .withPartitionId(1)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    handler.export(record);

    // then - should not create any records
    verify(jobMetricsBatchWriter, times(0)).create(any());
  }

  private List<ImmutableStatusMetricsValue> createStatusMetrics(
      final int createdCount, final int completedCount, final int failedCount) {
    return List.of(
        ImmutableStatusMetricsValue.builder()
            .withCount(createdCount)
            .withLastUpdatedAt(1100L)
            .build(),
        ImmutableStatusMetricsValue.builder()
            .withCount(completedCount)
            .withLastUpdatedAt(1200L)
            .build(),
        ImmutableStatusMetricsValue.builder()
            .withCount(failedCount)
            .withLastUpdatedAt(1300L)
            .build());
  }
}
