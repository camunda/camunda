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
import io.camunda.exporter.handlers.usage.UsageMetricTUExportedHandler.UsageMetricsTUBatch;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.zeebe.protocol.record.value.ImmutableUsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsageMetricTUExportedHandlerTest
    extends AbstractUsageMetricExportedHandlerTest<UsageMetricTUExportedHandler> {
  private static final String TU_ID_PATTERN = "%s_%s_%s";
  private static final long ASSIGNEE_HASH_1 = 1234567L;
  private static final long ASSIGNEE_HASH_2 = 7654321L;
  private static final int PARTITION_ID = 9;
  private static final String INDEX_NAME = "test-usage-metrics-tu";

  public UsageMetricTUExportedHandlerTest() {
    super(new UsageMetricTUExportedHandler(INDEX_NAME));
  }

  @Test
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.TU)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withSetValues(Map.of(TENANT_3, Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2)))
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(Long.parseLong(EVENT_KEY))
                    .withPartitionId(PARTITION_ID));
    final var usageMetricsBatch = new UsageMetricsTUBatch(EVENT_KEY);
    composeUsageMetricsTU(now).forEach(usageMetricsBatch::addMetric);

    // when
    underTest.updateEntity(record, usageMetricsBatch);

    // then
    final List<UsageMetricsTUEntity> tuVariables = usageMetricsBatch.getMetrics();

    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_1, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_2, ASSIGNEE_HASH_2),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_2));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getEndTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getStartTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getPartitionId)
        .containsOnly(PARTITION_ID);
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getAssigneeHash)
        .containsOnly(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2);
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var usageMetricsBatch = new UsageMetricsTUBatch(EVENT_KEY);
    composeUsageMetricsTU(now).forEach(usageMetricsBatch::addMetric);
    final TargetIndex index = TargetIndex.mainIndex("test-index");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(index, usageMetricsBatch, mockRequest);

    // then
    usageMetricsBatch.getMetrics().forEach(entity -> verify(mockRequest).add(index, entity));
    verifyNoMoreInteractions(mockRequest);
  }

  private ArrayList<UsageMetricsTUEntity> composeUsageMetricsTU(final long now) {
    return new ArrayList<>(
        List.of(
            new UsageMetricsTUEntity()
                .setId(String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_1, ASSIGNEE_HASH_1))
                .setPartitionId(PARTITION_ID)
                .setTenantId(TENANT_1)
                .setStartTime(DateUtil.toOffsetDateTime(now))
                .setEndTime(DateUtil.toOffsetDateTime(now))
                .setAssigneeHash(ASSIGNEE_HASH_1),
            new UsageMetricsTUEntity()
                .setId(String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_2, ASSIGNEE_HASH_2))
                .setPartitionId(PARTITION_ID)
                .setTenantId(TENANT_2)
                .setStartTime(DateUtil.toOffsetDateTime(now))
                .setEndTime(DateUtil.toOffsetDateTime(now))
                .setAssigneeHash(ASSIGNEE_HASH_2)));
  }
}
