/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.usage.UsageMetricExportedHandler.UsageMetricsBatch;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.protocol.record.value.ImmutableUsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageMetricExportedHandlerTest
    extends AbstractUsageMetricExportedHandlerTest<UsageMetricExportedHandler> {

  private static final String ID_PATTERN = "%s_%s";
  private static final int PARTITION_ID = 9;
  private static final String INDEX_NAME = "test-usage-metrics";

  public UsageMetricExportedHandlerTest() {
    super(new UsageMetricExportedHandler(INDEX_NAME));
  }

  @Test
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.RPI)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withCounterValues(Map.of(TENANT_3, 33L))
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(Long.parseLong(EVENT_KEY))
                    .withPartitionId(PARTITION_ID));

    final var existingMetrics = composeUsageMetrics(now, 11L, 22L);
    final var usageMetricsBatch = new UsageMetricsBatch(EVENT_KEY);
    existingMetrics.forEach(usageMetricsBatch::addMetric);

    // when
    underTest.updateEntity(record, usageMetricsBatch);

    // then
    final List<UsageMetricsEntity> metrics = usageMetricsBatch.getMetrics();

    assertThat(metrics)
        .extracting(UsageMetricsEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(ID_PATTERN, EVENT_KEY, TENANT_1),
            String.format(ID_PATTERN, EVENT_KEY, TENANT_2),
            String.format(ID_PATTERN, EVENT_KEY, TENANT_3));
    assertThat(metrics)
        .extracting(UsageMetricsEntity::getEndTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(metrics)
        .extracting(UsageMetricsEntity::getStartTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(metrics)
        .extracting(UsageMetricsEntity::getEventType)
        .containsOnly(UsageMetricsEventType.RPI);
    assertThat(metrics).extracting(UsageMetricsEntity::getPartitionId).containsOnly(PARTITION_ID);
    assertThat(metrics)
        .extracting(UsageMetricsEntity::getEventValue)
        .containsExactlyInAnyOrder(11L, 22L, 33L);
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var existingMetrics = composeUsageMetrics(now, 11L, 22L);
    final var usageMetricsBatch = new UsageMetricsBatch(EVENT_KEY);
    existingMetrics.forEach(usageMetricsBatch::addMetric);
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, usageMetricsBatch, mockRequest);

    // then
    usageMetricsBatch.getMetrics().forEach(entity -> verify(mockRequest).add(index, entity));
    verifyNoMoreInteractions(mockRequest);
  }

  private ArrayList<UsageMetricsEntity> composeUsageMetrics(
      final long now, final Long... eventValues) {
    return new ArrayList<>(
        List.of(
            new UsageMetricsEntity()
                .setId(String.format(ID_PATTERN, EVENT_KEY, TENANT_1))
                .setPartitionId(PARTITION_ID)
                .setStartTime(DateUtil.toOffsetDateTime(now))
                .setEndTime(DateUtil.toOffsetDateTime(now))
                .setEventType(UsageMetricsEventType.RPI)
                .setEventValue(eventValues[0]),
            new UsageMetricsEntity()
                .setId(String.format(ID_PATTERN, EVENT_KEY, TENANT_2))
                .setEventType(UsageMetricsEventType.RPI)
                .setPartitionId(PARTITION_ID)
                .setStartTime(DateUtil.toOffsetDateTime(now))
                .setEndTime(DateUtil.toOffsetDateTime(now))
                .setEventValue(eventValues[1])));
  }
}
