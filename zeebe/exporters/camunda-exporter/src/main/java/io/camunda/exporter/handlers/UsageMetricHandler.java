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

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UsageMetricHandler(String indexName)
    implements ExportHandler<UsageMetricsEntity, UsageMetricRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(UsageMetricHandler.class);
  private static final String ID_PATTERN = "%s_%s";

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USAGE_METRIC;
  }

  @Override
  public Class<UsageMetricsEntity> getEntityType() {
    return UsageMetricsEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UsageMetricRecordValue> record) {
    return UsageMetricIntent.EXPORTED.equals(record.getIntent())
        && !EventType.NONE.equals(record.getValue().getEventType());
  }

  @Override
  public List<String> generateIds(final Record<UsageMetricRecordValue> record) {
    final long key = record.getKey();
    return record.getValue().getCounterValues().keySet().stream()
        .map(tenantId -> ID_PATTERN.formatted(key, tenantId))
        .toList();
  }

  @Override
  public UsageMetricsEntity createNewEntity(final String id) {
    return new UsageMetricsEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<UsageMetricRecordValue> record, final UsageMetricsEntity entity) {
    final var recordValue = record.getValue();
    final var tenantId = parseTenantId(entity.getId());

    final var eventType = mapEventType(recordValue.getEventType());

    if (eventType != null) {
      final var tenantValue = recordValue.getCounterValues().get(tenantId);
      entity
          .setEventTime(DateUtil.toOffsetDateTime(recordValue.getStartTime()))
          .setEventType(eventType)
          .setEventValue(tenantValue)
          .setTenantId(tenantId)
          .setPartitionId(record.getPartitionId());
    } else {
      LOGGER.warn("Unsupported event type: {}", recordValue.getEventType());
    }
  }

  @Override
  public void flush(final UsageMetricsEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private String parseTenantId(final String id) {
    final int idx = id.indexOf('_');
    return id.substring(idx + 1);
  }

  private UsageMetricsEventType mapEventType(final EventType eventType) {
    return switch (eventType) {
      case RPI -> RPI;
      case EDI -> EDI;
      default -> null;
    };
  }
}
