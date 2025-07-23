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
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UsageMetricHandler(String indexName, String tuIndexName)
    implements ExportHandler<UsageMetricsBatch, UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricHandler.class);
  private static final String ID_PATTERN = "%s_%s";
  private static final String TU_ID_PATTERN = "%s_%s_%s";

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
    return new UsageMetricsBatch(id, new ArrayList<>(), new ArrayList<>());
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
    final var collection = usageMetricsBatch.variables();
    final var tuCollection = usageMetricsBatch.tuVariables();

    recordValue
        .getCounterValues()
        .forEach(
            (tenantId, counter) ->
                collection.add(
                    createUsageMetricEntity(
                        recordKey, partitionId, timestamp, tenantId, eventType, counter)));

    recordValue
        .getSetValues()
        .forEach(
            (tenantId, set) -> {
              collection.add(
                  createUsageMetricEntity(
                      recordKey, partitionId, timestamp, tenantId, eventType, set.size()));
              set.forEach(
                  setValue ->
                      tuCollection.add(
                          createUsageMetricTUEntity(
                              recordKey, partitionId, timestamp, tenantId, setValue)));
            });
  }

  @Override
  public void flush(final UsageMetricsBatch batch, final BatchRequest batchRequest) {
    batch.variables().forEach(v -> flushUsageMetricsBatch(v, batchRequest));
    batch.tuVariables().forEach(v -> flushUsageMetricsTUBatch(v, batchRequest));
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

  private UsageMetricsEntity createUsageMetricEntity(
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

  private UsageMetricsTUEntity createUsageMetricTUEntity(
      final long recordKey,
      final int partitionId,
      final OffsetDateTime timestamp,
      final String tenantId,
      final long assigneeHash) {

    return new UsageMetricsTUEntity()
        .setId(String.format(TU_ID_PATTERN, recordKey, tenantId, assigneeHash))
        .setPartitionId(partitionId)
        .setTenantId(tenantId)
        .setEventTime(timestamp)
        .setAssigneeHash(assigneeHash);
  }

  private void flushUsageMetricsBatch(
      final UsageMetricsEntity entity, final BatchRequest batchRequest) {
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(UsageMetricIndex.EVENT_VALUE, entity.getEventValue());
    updateFields.put(UsageMetricIndex.EVENT_TYPE, entity.getEventType());
    updateFields.put(UsageMetricIndex.EVENT_TIME, entity.getEventTime());
    updateFields.put(UsageMetricIndex.PARTITION_ID, entity.getPartitionId());
    updateFields.put(UsageMetricIndex.TENANT_ID, entity.getTenantId());
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  private void flushUsageMetricsTUBatch(
      final UsageMetricsTUEntity entity, final BatchRequest batchRequest) {
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(UsageMetricTUIndex.EVENT_TIME, entity.getEventTime());
    updateFields.put(UsageMetricTUIndex.ASSIGNEE_HASH, entity.getAssigneeHash());
    updateFields.put(UsageMetricTUIndex.PARTITION_ID, entity.getPartitionId());
    updateFields.put(UsageMetricTUIndex.TENANT_ID, entity.getTenantId());
    batchRequest.upsert(tuIndexName, entity.getId(), entity, updateFields);
  }

  public record UsageMetricsBatch(
      String id, List<UsageMetricsEntity> variables, List<UsageMetricsTUEntity> tuVariables)
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
