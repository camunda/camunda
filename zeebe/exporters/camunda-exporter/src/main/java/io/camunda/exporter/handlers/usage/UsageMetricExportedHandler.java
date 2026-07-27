/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.usage;

import io.camunda.exporter.handlers.usage.UsageMetricExportedHandler.UsageMetricsBatch;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

public class UsageMetricExportedHandler
    extends AbstractUsageMetricExportedHandler<UsageMetricsEntity, UsageMetricsBatch> {
  private static final String ID_PATTERN = "%s_%s";

  public UsageMetricExportedHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public Class<UsageMetricsBatch> getEntityType() {
    return UsageMetricsBatch.class;
  }

  @Override
  public UsageMetricsBatch createNewEntity(final String id) {
    return new UsageMetricsBatch(id);
  }

  @Override
  protected Stream<UsageMetricsEntity> extractMetrics(
      final Record<UsageMetricRecordValue> record, final UsageMetricsEventType eventType) {
    final var recordKey = record.getKey();
    final var partitionId = record.getPartitionId();
    final var recordValue = record.getValue();
    final var startTime = DateUtil.toOffsetDateTime(recordValue.getStartTime());
    final var endTime = DateUtil.toOffsetDateTime(recordValue.getEndTime());

    return recordValue.getCounterValues().entrySet().stream()
        .map(
            entry -> {
              final var tenantId = entry.getKey();
              final var counter = entry.getValue();
              return createUsageMetricEntity(
                  recordKey, partitionId, startTime, endTime, tenantId, eventType, counter);
            });
  }

  private UsageMetricsEntity createUsageMetricEntity(
      final long recordKey,
      final int partitionId,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final String tenantId,
      final UsageMetricsEventType eventType,
      final Long eventValue) {

    return new UsageMetricsEntity()
        .setId(String.format(ID_PATTERN, recordKey, tenantId))
        .setPartitionId(partitionId)
        .setTenantId(tenantId)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setEventType(eventType)
        .setEventValue(eventValue);
  }

  public static class UsageMetricsBatch extends Batch<UsageMetricsBatch, UsageMetricsEntity> {
    public UsageMetricsBatch(final String id) {
      super(id);
    }
  }
}
