/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.jobmetricsbatch.JobMetricsBatchEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobMetricsValue;
import io.camunda.zeebe.protocol.record.value.ImmutableStatusMetricsValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue.StatusMetricsValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class JobBatchMetricsExportedHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "jobMetricsBatch";
  private final JobBatchMetricsExportedHandler underTest =
      new JobBatchMetricsExportedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB_METRICS_BATCH);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(JobMetricsBatchEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobMetricsBatchIntent.class,
      names = {"EXPORT"},
      mode = Mode.EXCLUDE)
  void shouldHandleRecord(final JobMetricsBatchIntent intent) {
    // given
    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(ValueType.JOB_METRICS_BATCH, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobMetricsBatchIntent.class,
      names = {"EXPORTED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final JobMetricsBatchIntent intent) {
    // given
    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(ValueType.JOB_METRICS_BATCH, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
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
                        .withStatusMetrics(
                            List.of(
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(5)
                                    .withLastUpdatedAt(1500L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(3)
                                    .withLastUpdatedAt(1600L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(2)
                                    .withLastUpdatedAt(1700L)
                                    .build()))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(123L)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly("123_0_1_2");
  }

  @Test
  void shouldGenerateMultipleIds() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1", "jobType2", "tenant2"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics())
                        .build(),
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(3)
                        .withTenantIdIndex(4)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(createStatusMetrics())
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(456L)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly("456_0_1_2", "456_3_4_2");
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final JobMetricsBatchEntity inputEntity =
        new JobMetricsBatchEntity()
            .setId("id")
            .setTenantId("tenant1")
            .setJobType("jobType1")
            .setWorker("worker1");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long startTime = 1000L;
    final long endTime = 2000L;
    final long lastCreatedAt = 1500L;
    final long lastCompletedAt = 1600L;
    final long lastFailedAt = 1700L;

    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(startTime)
            .withBatchEndTime(endTime)
            .withRecordSizeLimitExceeded(true)
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
                                    .withCount(3)
                                    .withLastUpdatedAt(lastCompletedAt)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(2)
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
    final JobMetricsBatchEntity entity = new JobMetricsBatchEntity().setId("123_0_1_2");
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getPartitionId()).isEqualTo(1);
    assertThat(entity.getStartTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC));
    assertThat(entity.getEndTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC));
    assertThat(entity.isIncompleteBatch()).isTrue();
    assertThat(entity.getTenantId()).isEqualTo("tenant1");
    assertThat(entity.getJobType()).isEqualTo("jobType1");
    assertThat(entity.getWorker()).isEqualTo("worker1");
    assertThat(entity.getCreatedCount()).isEqualTo(5);
    assertThat(entity.getLastCreatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastCreatedAt), ZoneOffset.UTC));
    assertThat(entity.getCompletedCount()).isEqualTo(3);
    assertThat(entity.getLastCompletedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastCompletedAt), ZoneOffset.UTC));
    assertThat(entity.getFailedCount()).isEqualTo(2);
    assertThat(entity.getLastFailedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastFailedAt), ZoneOffset.UTC));
  }

  @Test
  void shouldUpdateEntityFromRecordWithMultipleMetrics() {
    // given
    final var jobMetricsBatchRecordValue =
        ImmutableJobMetricsBatchRecordValue.builder()
            .withBatchStartTime(1000L)
            .withBatchEndTime(2000L)
            .withRecordSizeLimitExceeded(false)
            .withEncodedStrings(List.of("jobType1", "tenant1", "worker1", "jobType2", "tenant2"))
            .withJobMetrics(
                List.of(
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(0)
                        .withTenantIdIndex(1)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(
                            List.of(
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(10)
                                    .withLastUpdatedAt(1100L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(20)
                                    .withLastUpdatedAt(1200L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(30)
                                    .withLastUpdatedAt(1300L)
                                    .build()))
                        .build(),
                    ImmutableJobMetricsValue.builder()
                        .withJobTypeIndex(3)
                        .withTenantIdIndex(4)
                        .withWorkerNameIndex(2)
                        .withStatusMetrics(
                            List.of(
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(100)
                                    .withLastUpdatedAt(1400L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(200)
                                    .withLastUpdatedAt(1500L)
                                    .build(),
                                ImmutableStatusMetricsValue.builder()
                                    .withCount(300)
                                    .withLastUpdatedAt(1600L)
                                    .build()))
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> record =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(789L)
                    .withPartitionId(2)
                    .withValue(jobMetricsBatchRecordValue));

    // when - update second metric
    final JobMetricsBatchEntity entity = new JobMetricsBatchEntity().setId("789_3_4_2");
    underTest.updateEntity(record, entity);

    // then - should get values from second metric
    assertThat(entity.getPartitionId()).isEqualTo(2);
    assertThat(entity.getTenantId()).isEqualTo("tenant2");
    assertThat(entity.getJobType()).isEqualTo("jobType2");
    assertThat(entity.getWorker()).isEqualTo("worker1");
    assertThat(entity.getCreatedCount()).isEqualTo(100);
    assertThat(entity.getCompletedCount()).isEqualTo(200);
    assertThat(entity.getFailedCount()).isEqualTo(300);
  }

  @Test
  void shouldMapPartitionIdFromRecord() {
    // given - test with partition 0
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
                        .withStatusMetrics(createStatusMetrics())
                        .build()))
            .build();

    final Record<JobMetricsBatchRecordValue> recordPartition0 =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(100L)
                    .withPartitionId(0)
                    .withValue(jobMetricsBatchRecordValue));

    final Record<JobMetricsBatchRecordValue> recordPartition5 =
        factory.generateRecord(
            ValueType.JOB_METRICS_BATCH,
            r ->
                r.withIntent(JobMetricsBatchIntent.EXPORTED)
                    .withKey(200L)
                    .withPartitionId(5)
                    .withValue(jobMetricsBatchRecordValue));

    // when
    final JobMetricsBatchEntity entityPartition0 = new JobMetricsBatchEntity().setId("100_0_1_2");
    underTest.updateEntity(recordPartition0, entityPartition0);

    final JobMetricsBatchEntity entityPartition5 = new JobMetricsBatchEntity().setId("200_0_1_2");
    underTest.updateEntity(recordPartition5, entityPartition5);

    // then
    assertThat(entityPartition0.getPartitionId()).isEqualTo(0);
    assertThat(entityPartition5.getPartitionId()).isEqualTo(5);
  }

  private List<StatusMetricsValue> createStatusMetrics() {
    return List.of(
        ImmutableStatusMetricsValue.builder().withCount(1).withLastUpdatedAt(1000L).build(),
        ImmutableStatusMetricsValue.builder().withCount(2).withLastUpdatedAt(1100L).build(),
        ImmutableStatusMetricsValue.builder().withCount(3).withLastUpdatedAt(1200L).build());
  }
}
