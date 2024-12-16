/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.Instant;
import java.util.List;

public class TaskCompletedMetricHandler
    implements ExportHandler<MetricEntity, UserTaskRecordValue> {

  protected static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  private final String indexName;

  public TaskCompletedMetricHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER_TASK;
  }

  @Override
  public Class<MetricEntity> getEntityType() {
    return MetricEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserTaskRecordValue> record) {
    return record.getIntent().equals(UserTaskIntent.COMPLETED);
  }

  @Override
  public List<String> generateIds(final Record<UserTaskRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getUserTaskKey()));
  }

  @Override
  public MetricEntity createNewEntity(final String id) {
    return new MetricEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<UserTaskRecordValue> record, final MetricEntity entity) {
    entity
        .setEvent(EVENT_TASK_COMPLETED_BY_ASSIGNEE)
        .setEventTime(ExporterUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setValue(record.getValue().getAssignee())
        .setTenantId(record.getValue().getTenantId());
  }

  @Override
  public void flush(final MetricEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
