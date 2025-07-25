/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.EDI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.RPI;
import static io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType.TU;

import io.camunda.exporter.handlers.UsageMetricHandler.UsageMetricsBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UsageMetricHandler(String indexName)
    implements ExportHandler<UsageMetricsBatch, UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricHandler.class);
  private static final String ID_PATTERN = "%s_%s";

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USAGE_METRIC;
  }

  @Override
  public Class<UsageMetricsBatch> getEntityType() {
    return UsageMetricsBatch.class;
  }

  @Override
  public boolean handlesRecord(final Record<UsageMetricRecordValue> record) {
    return UsageMetricIntent.EXPORTED.equals(record.getIntent())
        && !EventType.NONE.equals(record.getValue().getEventType());
  }

  @Override
  public List<String> generateIds(final Record<UsageMetricRecordValue> record) {
    final long key = record.getKey();
    final UsageMetricRecordValue value = record.getValue();

    return Stream.concat(
            value.getCounterValues().keySet().stream(), value.getSetValues().keySet().stream())
        .map(tenantId -> ID_PATTERN.formatted(key, tenantId))
        .toList();
  }

  @Override
  public UsageMetricsBatch createNewEntity(final String id) {
    return new UsageMetricsBatch(id, new ArrayList<>());
  }

  @Override
  public void updateEntity(
      final Record<UsageMetricRecordValue> record, final UsageMetricsBatch usageMetricsBatch) {

    final var recordValue = record.getValue();
    final var eventType = mapEventType(recordValue.getEventType());

    if (eventType == null) {
      LOGGER.warn("Unsupported event type: {}", recordValue.getEventType());
      return;
    }

    final var recordKey = record.getKey();
    final var partitionId = record.getPartitionId();
    final var timestamp = DateUtil.toOffsetDateTime(record.getTimestamp());
    var collection = usageMetricsBatch.variables();

    recordValue
        .getCounterValues()
        .forEach(
            (tenantId, counter) ->
                collection.add(
                    createMetricEntity(
                        recordKey, partitionId, timestamp, tenantId, eventType, counter)));

    recordValue
        .getSetValues()
        .forEach(
            (tenantId, set) ->
                collection.add(
                    createMetricEntity(
                        recordKey, partitionId, timestamp, tenantId, eventType, set.size())));
  }

  private UsageMetricsEntity createMetricEntity(
      final long recordKey,
      final int partitionId,
      final OffsetDateTime timestamp,
      final String tenantId,
      final UsageMetricsEventType eventType,
      final long eventValue) {

    return new UsageMetricsEntity()
        .setId(String.format(ID_PATTERN, recordKey, tenantId))
        .setPartitionId(partitionId)
        .setTenantId(tenantId)
        .setEventTime(timestamp)
        .setEventType(eventType)
        .setEventValue(eventValue);
  }

  @Override
  public void flush(final UsageMetricsBatch entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private UsageMetricsEventType mapEventType(final EventType eventType) {
    return switch (eventType) {
      case RPI -> RPI;
      case EDI -> EDI;
      case TU -> TU;
      default -> null;
    };
  }

  public record UsageMetricsBatch(String id, List<UsageMetricsEntity> variables)
      implements ExporterEntity<UsageMetricHandler.UsageMetricsBatch> {

    @Override
    public String getId() {
      return id;
    }

    @Override
    public UsageMetricHandler.UsageMetricsBatch setId(final String id) {
      throw new UnsupportedOperationException("Not allowed to set an id");
    }
  }
}
