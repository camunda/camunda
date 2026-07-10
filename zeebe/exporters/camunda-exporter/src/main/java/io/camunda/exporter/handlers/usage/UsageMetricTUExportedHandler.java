/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.usage;

import io.camunda.exporter.handlers.usage.UsageMetricTUExportedHandler.UsageMetricsTUBatch;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

public class UsageMetricTUExportedHandler
    extends AbstractUsageMetricExportedHandler<UsageMetricsTUEntity, UsageMetricsTUBatch> {
  private static final String TU_ID_PATTERN = "%s_%s_%s";

  public UsageMetricTUExportedHandler(final String indexName) {
    super(indexName);
  }

  @Override
  protected Stream<UsageMetricsTUEntity> extractMetrics(
      final Record<UsageMetricRecordValue> record, final UsageMetricsEventType eventType) {
    final var recordKey = record.getKey();
    final var partitionId = record.getPartitionId();
    final var recordValue = record.getValue();
    final var startTime = DateUtil.toOffsetDateTime(recordValue.getStartTime());
    final var endTime = DateUtil.toOffsetDateTime(recordValue.getEndTime());

    return recordValue.getSetValues().entrySet().stream()
        .flatMap(
            entry -> {
              final var tenantId = entry.getKey();
              final var set = entry.getValue();
              return set.stream()
                  .map(
                      setValue ->
                          createUsageMetricTUEntity(
                              recordKey, partitionId, startTime, endTime, tenantId, setValue));
            });
  }

  @Override
  public Class<UsageMetricsTUBatch> getEntityType() {
    return UsageMetricsTUBatch.class;
  }

  @Override
  public UsageMetricsTUBatch createNewEntity(final String id) {
    return new UsageMetricsTUBatch(id);
  }

  private UsageMetricsTUEntity createUsageMetricTUEntity(
      final long recordKey,
      final int partitionId,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final String tenantId,
      final long assigneeHash) {

    return new UsageMetricsTUEntity()
        .setId(String.format(TU_ID_PATTERN, recordKey, tenantId, assigneeHash))
        .setPartitionId(partitionId)
        .setTenantId(tenantId)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setAssigneeHash(assigneeHash);
  }

  public static class UsageMetricsTUBatch extends Batch<UsageMetricsTUBatch, UsageMetricsTUEntity> {
    public UsageMetricsTUBatch(final String id) {
      super(id);
    }
  }
}
