/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.*;
import io.camunda.zeebe.operate.exporter.util.OperateExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricFromProcessInstanceHandler
    implements ExportHandler<MetricEntity, ProcessInstanceRecordValue> {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MetricFromProcessInstanceHandler.class);

  private final MetricIndex metricIndex;

  public MetricFromProcessInstanceHandler(MetricIndex metricIndex) {
    this.metricIndex = metricIndex;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<MetricEntity> getEntityType() {
    return MetricEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    return isRootProcessInstance && intentStr.equals(ELEMENT_ACTIVATING.name());
  }

  @Override
  public List<String> generateIds(Record<ProcessInstanceRecordValue> record) {
    return List.of();
  }

  @Override
  public MetricEntity createNewEntity(String id) {
    return new MetricEntity();
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record, MetricEntity entity) {
    final ProcessInstanceRecordValue recordValue = record.getValue();
    final String processInstanceKey = String.valueOf(recordValue.getProcessInstanceKey());
    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final String tenantId = OperateExportUtil.tenantOrDefault(recordValue.getTenantId());
    entity
        .setEvent(MetricsStore.EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }

  @Override
  public void flush(MetricEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(metricIndex.getFullQualifiedName(), entity);
  }

  @Override
  public String getIndexName() {
    return metricIndex.getFullQualifiedName();
  }

  private MetricEntity createProcessInstanceStartedKey(
      String processInstanceKey, String tenantId, OffsetDateTime timestamp) {
    return new MetricEntity()
        .setEvent(MetricsStore.EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(processInstanceKey)
        .setEventTime(timestamp)
        .setTenantId(tenantId);
  }
}
