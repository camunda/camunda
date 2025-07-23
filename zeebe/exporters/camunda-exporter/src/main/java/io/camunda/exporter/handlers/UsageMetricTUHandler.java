/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.handlers.UsageMetricTUHandler.UsageMetricsTUBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricTUHandler
    implements ExportHandler<UsageMetricsTUBatch, UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricTUHandler.class);
  private static final String ID_PATTERN = "%s_%s";
  private final String indexName;

  public UsageMetricTUHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USAGE_METRIC_TU;
  }

  @Override
  public Class<UsageMetricsTUBatch> getEntityType() {
    return UsageMetricsTUBatch.class;
  }

  @Override
  public boolean handlesRecord(final Record<UsageMetricRecordValue> usageMetricRecordValue) {
    return UsageMetricIntent.EXPORTED.equals(usageMetricRecordValue.getIntent())
        && EventType.TU.equals(usageMetricRecordValue.getValue().getEventType());
  }

  @Override
  public List<String> generateIds(final Record<UsageMetricRecordValue> record) {
    final long key = record.getKey();
    return record.getValue().getSetValues().keySet().stream()
        .map(tenantId -> ID_PATTERN.formatted(key, tenantId))
        .toList();
  }

  @Override
  public UsageMetricsTUBatch createNewEntity(final String id) {
    return new UsageMetricsTUBatch(id, new ArrayList<>());
  }

  @Override
  public void updateEntity(
      final Record<UsageMetricRecordValue> record, final UsageMetricsTUBatch usageMetricsTUBatch) {

    final var recordKey = record.getKey();
    final var recordValue = record.getValue();
    final var partitionId = record.getPartitionId();
    final var timestamp = DateUtil.toOffsetDateTime(record.getTimestamp());

    var usageMetricsCollection = usageMetricsTUBatch.variables();

    recordValue
        .getSetValues()
        .forEach(
            (tenantId, set) ->
                set.forEach(
                    value ->
                        usageMetricsCollection.add(
                            createMetricTUEntity(
                                recordKey, partitionId, timestamp, tenantId, value))));
  }

  private UsageMetricsTUEntity createMetricTUEntity(
      final long recordKey,
      final int partitionId,
      final OffsetDateTime timestamp,
      final String tenantId,
      final long eventValue) {

    return new UsageMetricsTUEntity()
        .setId(String.format(ID_PATTERN, recordKey, tenantId))
        .setPartitionId(partitionId)
        .setTenantId(tenantId)
        .setEventTime(timestamp)
        .setAssigneeHash(eventValue);
  }

  @Override
  public void flush(final UsageMetricsTUBatch entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  public record UsageMetricsTUBatch(String id, List<UsageMetricsTUEntity> variables)
      implements ExporterEntity<UsageMetricsTUBatch> {

    @Override
    public String getId() {
      return id;
    }

    @Override
    public UsageMetricsTUBatch setId(final String id) {
      throw new UnsupportedOperationException("Not allowed to set an id");
    }
  }
}
